package app.platform.domain.error;

public class NotFoundException extends PlatformException {
    public NotFoundException(String what, Object id) {
        super("NOT_FOUND", "%s %s not found".formatted(what, id));
    }
}
