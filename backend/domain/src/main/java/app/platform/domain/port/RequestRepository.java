package app.platform.domain.port;

import app.platform.domain.model.Request;
import app.platform.domain.model.RequestEvent;
import app.platform.domain.model.RequestStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestRepository {

    Request save(Request request);

    Optional<Request> findById(UUID id);

    Optional<Request> findByStackId(String stackId);

    List<Request> findByRequester(String requesterId, int limit);

    /** Append-only lifecycle event; source is "PLATFORM" or "CLOUDFORMATION". */
    void appendEvent(UUID requestId, RequestStatus from, RequestStatus to, String reason, String source, Instant at);

    List<RequestEvent> listEvents(UUID requestId);
}
