package app.platform.domain.error;

import java.util.Set;

/**
 * Render-time hard error (3.08): silently dropping user input is the worst failure mode, so any
 * schema field without a parameterMap entry aborts the render.
 */
public class UnmappedFieldException extends PlatformException {
    public UnmappedFieldException(Set<String> unmapped) {
        super("UNMAPPED_SCHEMA_FIELDS", "Schema fields have no parameterMap entry: " + String.join(", ", unmapped));
    }
}
