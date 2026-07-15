package app.platform.worker.handler;

import app.platform.domain.model.Manifest;
import app.platform.domain.model.Request;
import app.platform.domain.model.RequestStatus;
import app.platform.domain.port.RequestRepository;
import app.platform.domain.port.StackLauncher;
import app.platform.domain.port.TemplateRepository;
import app.platform.domain.port.TemplateStore;
import app.platform.messaging.WorkMessage;
import app.platform.worker.consume.WorkDispatcher.Outcome;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DeleteHandler {

    private static final Logger log = LoggerFactory.getLogger(DeleteHandler.class);

    private final RequestRepository requests;
    private final TemplateRepository templates;
    private final TemplateStore templateStore;
    private final StackLauncher stackLauncher;
    private final AwsErrorClassifier errorClassifier;

    public DeleteHandler(
            RequestRepository requests,
            TemplateRepository templates,
            TemplateStore templateStore,
            StackLauncher stackLauncher,
            AwsErrorClassifier errorClassifier) {
        this.requests = requests;
        this.templates = templates;
        this.templateStore = templateStore;
        this.stackLauncher = stackLauncher;
        this.errorClassifier = errorClassifier;
    }

    public Outcome handle(WorkMessage work) {
        Request request = requests.findById(work.requestId()).orElse(null);
        if (request == null || request.status() != RequestStatus.DELETE_IN_PROGRESS) {
            return Outcome.DONE; // idempotent skip
        }
        if (request.stackId() == null) {
            // Nothing was ever created — complete the delete locally
            request.transitionTo(RequestStatus.DELETE_COMPLETE);
            requests.save(request);
            requests.appendEvent(
                    request.id(),
                    RequestStatus.DELETE_IN_PROGRESS,
                    RequestStatus.DELETE_COMPLETE,
                    "No stack to delete",
                    "PLATFORM",
                    Instant.now());
            return Outcome.DONE;
        }
        try {
            Manifest manifest = templates.findVersion(request.templateVersionId())
                    .map(v -> templateStore.fetchManifest(v.s3KeyManifest()))
                    .orElse(null);
            stackLauncher.deleteStack(
                    request.stackId(),
                    manifest == null ? null : manifest.executionRoleArn(),
                    request.environment(),
                    request.region());
            log.info("DeleteStack initiated for request {}", request.id());
            return Outcome.DONE; // EventBridge listener (3.05) flips to DELETE_COMPLETE
        } catch (Exception e) {
            if (errorClassifier.classify(e) == AwsErrorClassifier.Classification.RETRYABLE) {
                return Outcome.RETRYABLE;
            }
            RequestStatus from = request.status();
            request.recordFailure(e.getMessage());
            request.transitionTo(RequestStatus.DELETE_FAILED);
            requests.save(request);
            requests.appendEvent(request.id(), from, RequestStatus.DELETE_FAILED, e.getMessage(), "PLATFORM",
                    Instant.now());
            return Outcome.DONE;
        }
    }
}
