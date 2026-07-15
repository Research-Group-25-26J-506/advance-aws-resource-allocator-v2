package app.platform.worker.consume;

import app.platform.messaging.WorkMessage;
import app.platform.worker.handler.DeleteHandler;
import app.platform.worker.handler.ProvisionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;

/** Branches on message.type (3.04). TEMPLATE_SYNC / DEPLOY / DRIFT_CHECK land in later phases. */
@Component
public class WorkDispatcher {

    public enum Outcome {
        DONE,
        RETRYABLE
    }

    private static final Logger log = LoggerFactory.getLogger(WorkDispatcher.class);

    private final ProvisionHandler provisionHandler;
    private final DeleteHandler deleteHandler;

    public WorkDispatcher(ProvisionHandler provisionHandler, DeleteHandler deleteHandler) {
        this.provisionHandler = provisionHandler;
        this.deleteHandler = deleteHandler;
    }

    public Outcome dispatch(WorkMessage work, Message raw) {
        return switch (work.type()) {
            case PROVISION -> provisionHandler.handle(work);
            case DELETE -> deleteHandler.handle(work);
            case DEPLOY, TEMPLATE_SYNC, DRIFT_CHECK -> {
                log.warn("Handler for {} not implemented yet (phase 2/3) — dropping", work.type());
                yield Outcome.DONE;
            }
        };
    }
}
