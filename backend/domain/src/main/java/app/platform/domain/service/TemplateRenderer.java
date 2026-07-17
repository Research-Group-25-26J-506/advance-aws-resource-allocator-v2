package app.platform.domain.service;

import app.platform.domain.error.UnmappedFieldException;
import app.platform.domain.model.Manifest;
import app.platform.domain.port.BodyTemplater;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps user-facing form data to CloudFormation parameters (3.08). The user-facing JSON Schema is
 * NOT 1:1 with CFN parameters — the mapping is declared in the manifest's parameterMap
 * ("schema.path.dot.notation" -> "CfnParamName"). In JINJA mode the whole form-data map is handed
 * to the sandboxed BodyTemplater instead.
 */
public class TemplateRenderer {

    public record RenderResult(String body, Map<String, String> parameters) {}

    /** Bodies above the CFN inline limit must be uploaded and passed as TemplateURL. */
    public static final int CFN_INLINE_BODY_LIMIT = 51_200;

    private final BodyTemplater bodyTemplater;

    public TemplateRenderer(BodyTemplater bodyTemplater) {
        this.bodyTemplater = bodyTemplater;
    }

    public RenderResult render(Manifest manifest, Map<String, Object> formData, String cfnBody) {
        Map<String, Object> withDefaults = applyDefaults(manifest, formData);
        return switch (manifest.renderMode()) {
            case PARAMETER -> new RenderResult(cfnBody, mapParameters(manifest, withDefaults));
            // mutable map: the worker injects platform parameters (Environment) post-render
            case JINJA -> new RenderResult(bodyTemplater.render(cfnBody, withDefaults), new LinkedHashMap<>());
        };
    }

    private Map<String, Object> applyDefaults(Manifest manifest, Map<String, Object> formData) {
        Map<String, Object> merged = new HashMap<>(formData);
        // defaultsByEnv fill gaps only — user input always wins
        Map<String, Map<String, Object>> defaults = manifest.defaultsByEnv();
        if (defaults != null) {
            defaults.values().forEach(envDefaults -> envDefaults.forEach(merged::putIfAbsent));
        }
        return merged;
    }

    private Map<String, String> mapParameters(Manifest manifest, Map<String, Object> formData) {
        Map<String, String> params = new LinkedHashMap<>();
        Set<String> consumed = new HashSet<>();

        manifest.parameterMap().forEach((schemaPath, cfnParam) -> {
            Object value = valueAt(formData, schemaPath);
            if (value != null) {
                params.put(cfnParam, stringify(value));
                consumed.add(schemaPath.split("\\.")[0]);
            }
        });

        // Unmapped user input is a hard error — never silently drop what the user asked for.
        Set<String> unmapped = new HashSet<>(formData.keySet());
        unmapped.removeAll(consumed);
        if (!unmapped.isEmpty()) {
            throw new UnmappedFieldException(unmapped);
        }
        return params;
    }

    private Object valueAt(Map<String, Object> data, String dotPath) {
        Object current = data;
        for (String segment : dotPath.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private String stringify(Object value) {
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        return String.valueOf(value);
    }
}
