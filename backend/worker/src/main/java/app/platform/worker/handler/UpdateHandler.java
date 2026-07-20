package app.platform.worker.handler;

import app.platform.domain.model.Manifest;
import app.platform.domain.model.Request;
import app.platform.domain.model.RequestStatus;
import app.platform.domain.model.Team;
import app.platform.domain.model.TemplateVersion;
import app.platform.domain.port.RequestRepository;
import app.platform.domain.port.StackLauncher;
import app.platform.domain.port.TemplateRepository;
import app.platform.domain.port.TemplateStore;
import app.platform.domain.service.TagPolicyService;
import app.platform.domain.service.TemplateRenderer;
import app.platform.messaging.WorkMessage;
import app.platform.persistence.repo.SpringDataRepos;
import app.platform.worker.aws.ExecRoleResolver;
import app.platform.worker.aws.GroupClusterManager;
import app.platform.worker.consume.WorkDispatcher.Outcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * In-place update pipeline: load → render → tag → assume-role → UpdateStack → persist. Used to roll
 * a new container image onto an existing ecs-service (and to carry that image up on a promote)
 * WITHOUT tearing down and re-creating the stack. Same render+inject path as {@link ProvisionHandler}
 * so environment scoping, per-group clusters and ALB priorities stay identical to the create.
 * Idempotent: skips anything not UPDATE_IN_PROGRESS; CFN ClientRequestToken = the update's key.
 */
@Component
public class UpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(UpdateHandler.class);

    private final RequestRepository requests;
    private final TemplateRepository templates;
    private final TemplateStore templateStore;
    private final TemplateRenderer renderer;
    private final TagPolicyService tagPolicy;
    private final StackLauncher stackLauncher;
    private final AwsErrorClassifier errorClassifier;
    private final SpringDataRepos.Teams teams;
    private final ExecRoleResolver execRoles;
    private final GroupClusterManager groupClusters;
    private final Counter updated;
    private final Counter failed;

    public UpdateHandler(
            RequestRepository requests,
            TemplateRepository templates,
            TemplateStore templateStore,
            TemplateRenderer renderer,
            TagPolicyService tagPolicy,
            StackLauncher stackLauncher,
            AwsErrorClassifier errorClassifier,
            SpringDataRepos.Teams teams,
            ExecRoleResolver execRoles,
            GroupClusterManager groupClusters,
            MeterRegistry metrics) {
        this.requests = requests;
        this.templates = templates;
        this.templateStore = templateStore;
        this.renderer = renderer;
        this.tagPolicy = tagPolicy;
        this.stackLauncher = stackLauncher;
        this.errorClassifier = errorClassifier;
        this.teams = teams;
        this.execRoles = execRoles;
        this.groupClusters = groupClusters;
        this.updated = metrics.counter("stack_update_initiated");
        this.failed = metrics.counter("stack_update_failed");
    }

    public Outcome handle(WorkMessage work) {
        Request request = requests.findById(work.requestId()).orElse(null);
        if (request == null) {
            log.warn("Request {} vanished — dropping update message", work.requestId());
            return Outcome.DONE;
        }
        if (request.status() != RequestStatus.UPDATE_IN_PROGRESS) {
            log.info("Request {} is {} not UPDATE_IN_PROGRESS — idempotent skip", request.id(), request.status());
            return Outcome.DONE;
        }
        if (request.stackId() == null || request.stackId().isBlank()) {
            // No stack to update — treat as a validation failure rather than a hung request.
            return failRequest(request, new IllegalStateException("No stack to update"));
        }
        MDC.put("template_id", request.templateId());
        MDC.put("environment", request.environment().name());
        MDC.put("region", request.region());

        try {
            TemplateVersion version = templates
                    .findVersion(request.templateVersionId())
                    .orElseThrow(() -> new IllegalStateException("template version missing"));
            Manifest manifest = templateStore.fetchManifest(version.s3KeyManifest());
            String cfnBody = templateStore.fetchBody(version.s3KeyBody());

            var rendered = renderer.render(manifest, request.formData(), cfnBody);
            // Identical platform-injected parameters to the create path — see ProvisionHandler.
            if (cfnBody.matches("(?s).*\\n {2}Environment:\\s*\\n.*")) {
                rendered.parameters().put("Environment", request.environment().name().toLowerCase());
            }
            if (cfnBody.matches("(?s).*\\n {2}ClusterName:\\s*\\n.*")) {
                rendered.parameters().put(
                        "ClusterName",
                        groupClusters.ensureCluster(request.resourceName(), request.environment().name()));
            }
            if (cfnBody.matches("(?s).*\\n {2}AlbPriority:\\s*\\n.*")) {
                Object svc = request.formData().get("serviceName");
                String key = (svc == null ? request.resourceName() : svc.toString())
                        + ":" + request.environment().name();
                rendered.parameters().put("AlbPriority", String.valueOf(1000 + Math.floorMod(key.hashCode(), 40000)));
            }
            Team team = teams.findById(request.teamId())
                    .map(t -> new Team(t.getId(), t.getName(), t.getCostCenter()))
                    .orElseThrow(() -> new IllegalStateException("team missing"));
            var tags = tagPolicy.mandatoryTags(request, team, version, request.templateId());

            stackLauncher.updateStack(new StackLauncher.StackLaunch(
                    request.stackId(), // update the EXISTING stack, addressed by its stored id
                    rendered.body().length() <= TemplateRenderer.CFN_INLINE_BODY_LIMIT ? rendered.body() : null,
                    null,
                    rendered.parameters(),
                    tags,
                    manifest.cfnCapabilities(),
                    execRoles.resolve(manifest.executionRoleArn(), request.templateId(), request.environment()),
                    request.environment(),
                    request.region(),
                    work.idempotencyKey()));

            updated.increment();
            requests.appendEvent(
                    request.id(),
                    RequestStatus.UPDATE_IN_PROGRESS,
                    RequestStatus.UPDATE_IN_PROGRESS,
                    "UpdateStack initiated for " + request.stackId(),
                    "PLATFORM",
                    Instant.now());
            log.info("Stack update initiated for request {}: {}", request.id(), request.stackId());
            return Outcome.DONE; // EventBridge listener (3.05) flips to UPDATE_COMPLETE

        } catch (Exception e) {
            // "No updates are to be performed" — the rendered template is identical to what's live
            // (e.g. the same image re-submitted). That's a success, not a failure: settle immediately.
            if (e.getMessage() != null && e.getMessage().contains("No updates are to be performed")) {
                request.transitionTo(RequestStatus.UPDATE_COMPLETE);
                requests.save(request);
                requests.appendEvent(
                        request.id(),
                        RequestStatus.UPDATE_IN_PROGRESS,
                        RequestStatus.UPDATE_COMPLETE,
                        "No changes to apply — already at the requested configuration",
                        "PLATFORM",
                        Instant.now());
                log.info("Update for request {} was a no-op — settled UPDATE_COMPLETE", request.id());
                return Outcome.DONE;
            }
            return failRequest(request, e);
        }
    }

    private Outcome failRequest(Request request, Exception e) {
        if (errorClassifier.classify(e) == AwsErrorClassifier.Classification.RETRYABLE) {
            log.warn("Retryable AWS error updating request {}: {}", request.id(), e.getMessage());
            return Outcome.RETRYABLE;
        }
        RequestStatus from = request.status();
        request.recordFailure(e.getMessage());
        request.transitionTo(RequestStatus.UPDATE_FAILED);
        requests.save(request);
        requests.appendEvent(request.id(), from, RequestStatus.UPDATE_FAILED, e.getMessage(), "PLATFORM", Instant.now());
        failed.increment();
        log.error("Request {} update failed terminally -> UPDATE_FAILED", request.id(), e);
        return Outcome.DONE;
    }
}
