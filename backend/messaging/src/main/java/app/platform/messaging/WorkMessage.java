package app.platform.messaging;

import java.util.UUID;

/** Envelope for everything on platform-requests. Worker branches on `type` (3.04). */
public record WorkMessage(MessageType type, UUID requestId, String idempotencyKey) {

    public enum MessageType {
        PROVISION,
        DEPLOY,
        DELETE,
        TEMPLATE_SYNC,
        DRIFT_CHECK
    }
}
