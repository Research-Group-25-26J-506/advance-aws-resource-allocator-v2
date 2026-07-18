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
import app.platform.worker.consume.WorkDispatcher.Outcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Provision pipeline (3.04): load → render → tag → assume-role → CreateStack → persist.
 * Idempotent: skips anything not QUEUED; CFN ClientRequestToken = idempotency key.
 */
@Component
public class ProvisionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProvisionHandler.class);

    private final RequestRepository requests;
    private final TemplateRepository templates;
    private final TemplateStore templateStore;
    private final TemplateRenderer renderer;
    private final TagPolicyService tagPolicy;
    private final StackLauncher stackLauncher;
    private final AwsErrorClassifier errorClassifier;
    private final SpringDataRepos.Teams teams;
    private final ExecRoleResolver execRoles;
    private final Counter created;
    private final Counter failed;

    public ProvisionHandler(
            RequestRepository requests,
            TemplateRepository templates,
            TemplateStore templateStore,
            TemplateRenderer renderer,
            TagPolicyService tagPolicy,
            StackLauncher stackLauncher,
            AwsErrorClassifier errorClassifier,
            SpringDataRepos.Teams teams,
            ExecRoleResolver execRoles,
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
        this.created = metrics.counter("stack_create_initiated");
        this.failed = metrics.counter("stack_create_failed");
    }

    public Outcome handle(WorkMessage work) {
        Request request = requests.findById(work.requestId()).orElse(null);
        if (request == null) {
            log.warn("Request {} vanished — dropping message", work.requestId());
            return Outcome.DONE;
        }
        if (request.status() != RequestStatus.QUEUED) {
            log.info("Request {} is {} not QUEUED — idempotent skip", request.id(), request.status());
            return Outcome.DONE;
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
            // Platform-injected parameter: templates declaring `Environment` get the request's
            // environment (lowercased) so physical names are env-suffixed and promotions never
            // collide (deltaalpha-...-dev vs -qa vs -stg vs -prod).
            if (cfnBody.matches("(?s).*\\n {2}Environment:\\s*\\n.*")) {
                rendered.parameters().put("Environment", request.environment().name().toLowerCase());
            }
            Team team = teams.findById(request.teamId())
                    .map(t -> new Team(t.getId(), t.getName(), t.getCostCenter()))
                    .orElseThrow(() -> new IllegalStateException("team missing"));
            var tags = tagPolicy.mandatoryTags(
                    request, team, version, request.templateId());

            String stackId = stackLauncher.createStack(new StackLauncher.StackLaunch(
                    stackName(request),
                    rendered.body().length() <= TemplateRenderer.CFN_INLINE_BODY_LIMIT ? rendered.body() : null,
                    null, // >51,200-byte bodies: upload to cfn-uploads + TemplateURL (adapter TODO)
                    rendered.parameters(),
                    tags,
                    manifest.cfnCapabilities(),
                    execRoles.resolve(manifest.executionRoleArn(), request.templateId(), request.environment()),
                    request.environment(),
                    request.region(),
                    work.idempotencyKey()));

            request.recordStackId(stackId);
            request.transitionTo(RequestStatus.CREATE_IN_PROGRESS);
            requests.save(request);
            requests.appendEvent(
                    request.id(),
                    RequestStatus.QUEUED,
                    RequestStatus.CREATE_IN_PROGRESS,
                    "CreateStack initiated: " + stackId,
                    "PLATFORM",
                    Instant.now());
            created.increment();
            log.info("Stack creation initiated for request {}: {}", request.id(), stackId);
            return Outcome.DONE;

        } catch (Exception e) {
            return failRequest(request, e);
        }
    }

    private Outcome failRequest(Request request, Exception e) {
        AwsErrorClassifier.Classification classification = errorClassifier.classify(e);
        if (classification == AwsErrorClassifier.Classification.RETRYABLE) {
            log.warn("Retryable AWS error for request {}: {}", request.id(), e.getMessage());
            return Outcome.RETRYABLE;
        }
        RequestStatus target = classification == AwsErrorClassifier.Classification.VALIDATION
                ? RequestStatus.FAILED_VALIDATION
                : RequestStatus.CREATE_FAILED;
        RequestStatus from = request.status();
        request.recordFailure(e.getMessage());
        request.transitionTo(target);
        requests.save(request);
        requests.appendEvent(request.id(), from, target, e.getMessage(), "PLATFORM", Instant.now());
        failed.increment();
        log.error("Request {} failed terminally -> {}", request.id(), target, e);
        return Outcome.DONE;
    }

    private String stackName(Request request) {
        // Use the RANDOM tail of the UUIDv7, not its first 8 hex: those are a millisecond-timestamp
        // prefix that only rolls over every ~65s, so two requests for the same template+resourceName
        // within that window (classically a group restore, or two quick provisions) produced an
        // IDENTICAL stack name and the second collided with "stack already exists". The last 8 hex
        // are rand_b — still stable per request (so retries/idempotency are unaffected) but unique.
        String compact = request.id().toString().replace("-", "");
        String suffix = compact.substring(compact.length() - 8);
        return "platform-%s-%s-%s".formatted(request.templateId(), request.resourceName(), suffix);
    }
}
