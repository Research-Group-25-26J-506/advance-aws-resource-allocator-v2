package app.platform.domain.error;

import app.platform.domain.model.RequestStatus;
import java.util.UUID;

public class IllegalTransitionException extends PlatformException {
    public IllegalTransitionException(UUID requestId, RequestStatus from, RequestStatus to) {
        super("ILLEGAL_TRANSITION", "Request %s cannot move %s -> %s".formatted(requestId, from, to));
    }
}
