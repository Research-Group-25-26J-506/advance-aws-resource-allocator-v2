package app.platform.domain.model;

import java.time.Instant;

/** One lifecycle transition; `from` is null for the initial event. Source: PLATFORM or CLOUDFORMATION. */
public record RequestEvent(RequestStatus from, RequestStatus to, String reason, String source, Instant occurredAt) {}
