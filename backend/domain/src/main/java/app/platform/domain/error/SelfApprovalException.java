package app.platform.domain.error;

import java.util.UUID;

/** Separation of duties: a requester may not approve their own request. */
public class SelfApprovalException extends PlatformException {
    public SelfApprovalException(UUID requestId) {
        super("SELF_APPROVAL_FORBIDDEN", "You cannot approve your own request (" + requestId + ")");
    }
}
