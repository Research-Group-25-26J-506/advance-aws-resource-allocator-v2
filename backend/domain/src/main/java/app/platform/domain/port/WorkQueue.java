package app.platform.domain.port;

import java.util.UUID;

/** API-side enqueue port (SQS adapter in :messaging). Traceparent rides as a message attribute. */
public interface WorkQueue {

    void enqueueProvision(UUID requestId, String idempotencyKey);

    /** In-place stack update (e.g. a new container image on an existing ecs-service). */
    void enqueueUpdate(UUID requestId, String idempotencyKey);

    void enqueueDelete(UUID requestId, String idempotencyKey);

    void enqueueTemplateSync(UUID syncId, String idempotencyKey);
}
