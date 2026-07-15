package app.platform.domain.model;

import java.util.Map;
import java.util.Set;

/**
 * Full request lifecycle. Mirrored 1:1 by the OpenAPI RequestStatus enum and the frontend
 * PlatformStatus component — add here, add there, or the build fails.
 */
public enum RequestStatus {
    DRAFT,
    PENDING_VALIDATION,
    FAILED_VALIDATION,
    PENDING_APPROVAL,
    REJECTED,
    QUEUED,
    CREATE_IN_PROGRESS,
    CREATE_COMPLETE,
    CREATE_FAILED,
    ROLLBACK_IN_PROGRESS,
    ROLLBACK_COMPLETE,
    ROLLBACK_FAILED,
    UPDATE_IN_PROGRESS,
    UPDATE_COMPLETE,
    UPDATE_FAILED,
    UPDATE_ROLLBACK_IN_PROGRESS,
    UPDATE_ROLLBACK_COMPLETE,
    DELETE_IN_PROGRESS,
    DELETE_COMPLETE,
    DELETE_FAILED;

    private static final Map<RequestStatus, Set<RequestStatus>> LEGAL_TRANSITIONS = Map.ofEntries(
            Map.entry(DRAFT, Set.of(PENDING_VALIDATION)),
            Map.entry(PENDING_VALIDATION, Set.of(FAILED_VALIDATION, PENDING_APPROVAL, QUEUED)),
            Map.entry(FAILED_VALIDATION, Set.of(PENDING_VALIDATION)),
            Map.entry(PENDING_APPROVAL, Set.of(REJECTED, QUEUED)),
            Map.entry(REJECTED, Set.of()),
            Map.entry(QUEUED, Set.of(CREATE_IN_PROGRESS, CREATE_FAILED, FAILED_VALIDATION)),
            Map.entry(CREATE_IN_PROGRESS, Set.of(CREATE_COMPLETE, CREATE_FAILED, ROLLBACK_IN_PROGRESS)),
            Map.entry(CREATE_COMPLETE, Set.of(UPDATE_IN_PROGRESS, DELETE_IN_PROGRESS)),
            Map.entry(CREATE_FAILED, Set.of(QUEUED, DELETE_IN_PROGRESS)),
            Map.entry(ROLLBACK_IN_PROGRESS, Set.of(ROLLBACK_COMPLETE, ROLLBACK_FAILED)),
            Map.entry(ROLLBACK_COMPLETE, Set.of(QUEUED, DELETE_IN_PROGRESS)),
            Map.entry(ROLLBACK_FAILED, Set.of(DELETE_IN_PROGRESS)),
            Map.entry(UPDATE_IN_PROGRESS, Set.of(UPDATE_COMPLETE, UPDATE_FAILED, UPDATE_ROLLBACK_IN_PROGRESS)),
            Map.entry(UPDATE_COMPLETE, Set.of(UPDATE_IN_PROGRESS, DELETE_IN_PROGRESS)),
            Map.entry(UPDATE_FAILED, Set.of(UPDATE_ROLLBACK_IN_PROGRESS, DELETE_IN_PROGRESS)),
            Map.entry(UPDATE_ROLLBACK_IN_PROGRESS, Set.of(UPDATE_ROLLBACK_COMPLETE, ROLLBACK_FAILED)),
            Map.entry(UPDATE_ROLLBACK_COMPLETE, Set.of(UPDATE_IN_PROGRESS, DELETE_IN_PROGRESS)),
            Map.entry(DELETE_IN_PROGRESS, Set.of(DELETE_COMPLETE, DELETE_FAILED)),
            Map.entry(DELETE_COMPLETE, Set.of()),
            Map.entry(DELETE_FAILED, Set.of(DELETE_IN_PROGRESS)));

    public boolean isInProgress() {
        return name().endsWith("_IN_PROGRESS");
    }

    /** Settled: no platform or CloudFormation work in flight (UI stops auto-refreshing here). */
    public boolean isTerminal() {
        return !isInProgress() && this != QUEUED && this != PENDING_VALIDATION && this != PENDING_APPROVAL
                && this != DRAFT;
    }

    public boolean canTransitionTo(RequestStatus next) {
        return LEGAL_TRANSITIONS.get(this).contains(next);
    }
}
