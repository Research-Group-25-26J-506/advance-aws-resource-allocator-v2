package app.platform.api.error;

import app.platform.domain.error.IllegalTransitionException;
import app.platform.domain.error.NotFoundException;
import app.platform.domain.error.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 Problem JSON everywhere, with the platform-specific `code` extension field (3.02). */
@RestControllerAdvice
public class ProblemAdvice {

    private static final Logger log = LoggerFactory.getLogger(ProblemAdvice.class);
    private static final String TYPE_BASE = "https://platform.internal/problems/";

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException e, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage(), e.code(), req);
    }

    @ExceptionHandler(IllegalTransitionException.class)
    public ProblemDetail conflict(IllegalTransitionException e, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, e.getMessage(), e.code(), req);
    }

    @ExceptionHandler(app.platform.domain.error.SelfApprovalException.class)
    public ProblemDetail forbidden(app.platform.domain.error.SelfApprovalException e, HttpServletRequest req) {
        return problem(HttpStatus.FORBIDDEN, e.getMessage(), e.code(), req);
    }

    @ExceptionHandler(PlatformException.class)
    public ProblemDetail platform(PlatformException e, HttpServletRequest req) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), e.code(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalid(MethodArgumentNotValidException e, HttpServletRequest req) {
        var detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("validation failed");
        return problem(HttpStatus.BAD_REQUEST, detail, "VALIDATION_FAILED", req);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception e, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "INTERNAL_ERROR", req);
    }

    private ProblemDetail problem(HttpStatus status, String detail, String code, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_BASE + code.toLowerCase()));
        pd.setTitle(status.getReasonPhrase());
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("code", code);
        return pd;
    }
}
