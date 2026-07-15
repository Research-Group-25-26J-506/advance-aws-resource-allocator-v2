package app.platform.templatesync;

import app.platform.domain.model.Manifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Validates manifest.json against manifest-v1.json (5.02) and maps it to the domain shape. */
@Component
public class ManifestParser {

    private final ObjectMapper mapper;
    private final JsonSchema schema;

    public ManifestParser(ObjectMapper mapper) {
        this.mapper = mapper;
        try (InputStream in = getClass().getResourceAsStream("/schemas/manifest-v1.json")) {
            this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
                    .getSchema(mapper.readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException("manifest-v1.json missing from classpath", e);
        }
    }

    public Manifest parse(String manifestJson) {
        try {
            JsonNode node = mapper.readTree(manifestJson);
            Set<ValidationMessage> errors = schema.validate(node);
            if (!errors.isEmpty()) {
                String detail = errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining("; "));
                throw new InvalidManifestException(detail);
            }
            return new Manifest(
                    node.get("templateId").asText(),
                    node.get("version").asText(),
                    node.get("displayName").asText(),
                    node.get("category").asText(),
                    Manifest.RenderMode.valueOf(
                            node.get("renderMode").asText().toUpperCase(Locale.ROOT)),
                    toStringMap(node.get("parameterMap")),
                    node.path("executionRoleArn").asText(null),
                    node.has("cfnCapabilities")
                            ? mapper.convertValue(node.get("cfnCapabilities"), List.class)
                            : List.of(),
                    node.has("defaultsByEnv")
                            ? mapper.convertValue(node.get("defaultsByEnv"), Map.class)
                            : Map.of(),
                    node.path("estimatedMonthlyCost").asText(null));
        } catch (IOException e) {
            throw new InvalidManifestException("manifest.json is not valid JSON: " + e.getMessage());
        }
    }

    private Map<String, String> toStringMap(JsonNode node) {
        Map<String, String> map = new HashMap<>();
        if (node != null) {
            node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
        }
        return map;
    }

    public static class InvalidManifestException extends RuntimeException {
        public InvalidManifestException(String message) {
            super(message);
        }
    }
}
