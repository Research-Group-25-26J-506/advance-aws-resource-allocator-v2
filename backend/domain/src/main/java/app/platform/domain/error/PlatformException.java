package app.platform.domain.error;

/** Base for domain errors. `code` maps to the RFC 9457 problem `code` field. */
public abstract class PlatformException extends RuntimeException {
    private final String code;

    protected PlatformException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
