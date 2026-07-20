package app.platform.domain.error;

/**
 * In-place update requested on a resource that has nothing to update (no container image in its
 * configuration). Only image-bearing templates (ecs-service) support the image-update flow.
 */
public class NotUpdatableException extends PlatformException {
    public NotUpdatableException(String templateId) {
        super("NOT_UPDATABLE", "Resource type '" + templateId + "' has no image to update in place");
    }
}
