package app.platform.worker.render;

import app.platform.domain.port.BodyTemplater;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Sandboxed Jinja renderer (3.08 enhancement): fail on unknown tokens, no filesystem access,
 * bounded output and render depth — template authors are semi-trusted; this must not be an RCE
 * surface.
 */
@Component
public class JinjavaBodyTemplater implements BodyTemplater {

    private final Jinjava jinjava;

    public JinjavaBodyTemplater() {
        this.jinjava = new Jinjava(JinjavaConfig.newBuilder()
                .withFailOnUnknownTokens(true)
                .withMaxOutputSize(1_000_000)
                .withMaxRenderDepth(10)
                .withEnableRecursiveMacroCalls(false)
                .build());
    }

    @Override
    public String render(String body, Map<String, Object> context) {
        var result = jinjava.renderForResult(body, context);
        if (result.hasErrors()) {
            throw new IllegalArgumentException("Jinja render failed: " + result.getErrors());
        }
        return result.getOutput();
    }
}
