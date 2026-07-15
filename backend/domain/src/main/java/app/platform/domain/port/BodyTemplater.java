package app.platform.domain.port;

import java.util.Map;

/**
 * Jinja-mode body templating (3.08). The adapter MUST be sandboxed: autoescape on, no
 * filesystem/module access, allow-listed filters, render timeout — template authors are
 * semi-trusted and this must not become an RCE surface.
 */
public interface BodyTemplater {

    String render(String body, Map<String, Object> context);
}
