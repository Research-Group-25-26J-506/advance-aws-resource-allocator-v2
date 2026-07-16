package app.platform.worker.consume;

import app.platform.messaging.WorkMessage;
import app.platform.templatesync.TemplateSyncHandler;
import app.platform.worker.handler.DeleteHandler;
import app.platform.worker.handler.ProvisionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;

/** Branches on message.type (3.04). DEPLOY / DRIFT_CHECK land in later phases. */
@Component
public class WorkDispatcher {

    public enum Outcome {
        DONE,
        RETRYABLE
    }

    private static final Logger log = LoggerFactory.getLogger(WorkDispatcher.class);

    private final ProvisionHandler provisionHandler;
    private final DeleteHandler deleteHandler;
    private final TemplateSyncHandler templateSyncHandler;

    public WorkDispatcher(
            ProvisionHandler provisionHandler,
            DeleteHandler deleteHandler,
            TemplateSyncHandler templateSyncHandler) {
        this.provisionHandler = provisionHandler;
        this.deleteHandler = deleteHandler;
        this.templateSyncHandler = templateSyncHandler;
    }

    public Outcome dispatch(WorkMessage work, Message raw) {
        return switch (work.type()) {
            case PROVISION -> provisionHandler.handle(work);
            case DELETE -> deleteHandler.handle(work);
            case TEMPLATE_SYNC -> {
                templateSyncHandler.handle(work.requestId()); // records its own SYNC_FAILED on error
                yield Outcome.DONE;
            }
            case DEPLOY, DRIFT_CHECK -> {
                log.warn("Handler for {} not implemented yet (phase 3+) — dropping", work.type());
                yield Outcome.DONE;
            }
        };
    }
}
