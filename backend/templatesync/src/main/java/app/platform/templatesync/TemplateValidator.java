package app.platform.templatesync;

import app.platform.domain.model.Manifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Validator pipeline (3.12), scoped: stages 1 (manifest), 2 (schema syntax + no external $ref),
 * 3' (template parses as YAML with CFN-shape checks — full cfn-lint/cfn-nag run in CI, versions
 * pinned there), 5 (parameterMap consistency), 8' (examples[0] validates against schema).
 * All stages run — no short-circuit — so authors get the complete report.
 */
@Service
public class TemplateValidator {

    public record StageResult(String stage, boolean passed, List<String> errors) {}

    public record Report(String templateId, String version, List<StageResult> stages) {
        public boolean passed() {
            return stages.stream().allMatch(StageResult::passed);
        }
    }

    private final ManifestParser manifestParser;
    private final ObjectMapper mapper;

    public TemplateValidator(ManifestParser manifestParser, ObjectMapper mapper) {
        this.manifestParser = manifestParser;
        this.mapper = mapper;
    }

    public Report validate(String templateId, String version, Path versionDir) throws Exception {
        List<StageResult> stages = new ArrayList<>();
        String manifestJson = readOrNull(versionDir.resolve("manifest.json"));
        String schemaJson = readOrNull(versionDir.resolve("schema.json"));
        String templateYaml = readOrNull(versionDir.resolve("template.yaml"));

        // Stage 1: manifest validates against manifest-v1
        Manifest manifest = null;
        List<String> stage1 = new ArrayList<>();
        if (manifestJson == null) {
            stage1.add("manifest.json missing");
        } else {
            try {
                manifest = manifestParser.parse(manifestJson);
                if (!manifest.templateId().equals(templateId) || !manifest.version().equals(version)) {
                    stage1.add("manifest templateId/version does not match directory %s/%s"
                            .formatted(templateId, version));
                }
            } catch (Exception e) {
                stage1.add(e.getMessage());
            }
        }
        stages.add(new StageResult("manifest-schema", stage1.isEmpty(), stage1));

        // Stage 2: schema.json is valid draft-07, no external $refs
        List<String> stage2 = new ArrayList<>();
        JsonSchema userSchema = null;
        JsonNode schemaNode = null;
        if (schemaJson == null) {
            stage2.add("schema.json missing");
        } else {
            try {
                schemaNode = mapper.readTree(schemaJson);
                userSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(schemaNode);
                findExternalRefs(schemaNode, stage2);
            } catch (Exception e) {
                stage2.add("schema.json invalid: " + e.getMessage());
            }
        }
        stages.add(new StageResult("schema-syntax", stage2.isEmpty(), stage2));

        // Stage 3 (scoped): template.yaml parses; has Resources; declared Parameters extracted
        List<String> stage3 = new ArrayList<>();
        Set<String> cfnParams = new HashSet<>();
        if (templateYaml == null) {
            stage3.add("template.yaml missing");
        } else {
            try {
                LoaderOptions options = new LoaderOptions();
                // CFN short-form tags (!Ref etc.) aren't SafeConstructor types; strip them first
                String plain = templateYaml.replaceAll("!\\w+", "");
                Object parsed = new Yaml(new SafeConstructor(options)).load(plain);
                if (parsed instanceof Map<?, ?> doc) {
                    if (!doc.containsKey("Resources")) {
                        stage3.add("template.yaml has no Resources section");
                    }
                    Object params = doc.get("Parameters");
                    if (params instanceof Map<?, ?> pm) {
                        pm.keySet().forEach(k -> cfnParams.add(String.valueOf(k)));
                    }
                } else {
                    stage3.add("template.yaml is not a mapping document");
                }
            } catch (Exception e) {
                stage3.add("template.yaml unparseable: " + e.getMessage());
            }
        }
        stages.add(new StageResult("cfn-syntax", stage3.isEmpty(), stage3));

        // Stage 5: parameterMap consistency (both directions)
        List<String> stage5 = new ArrayList<>();
        if (manifest != null && manifest.renderMode() == Manifest.RenderMode.PARAMETER && schemaNode != null) {
            for (Map.Entry<String, String> e : manifest.parameterMap().entrySet()) {
                if (!cfnParams.contains(e.getValue())) {
                    stage5.add("parameterMap targets missing CFN parameter: " + e.getValue());
                }
            }
            JsonNode required = schemaNode.path("required");
            for (JsonNode field : required) {
                if (!manifest.parameterMap().containsKey(field.asText())) {
                    stage5.add("required schema field has no parameterMap entry: " + field.asText());
                }
            }
        }
        stages.add(new StageResult("parameter-map", stage5.isEmpty(), stage5));

        // Stage 8 (scoped): examples[0] must validate against the user schema
        List<String> stage8 = new ArrayList<>();
        if (manifestJson != null && userSchema != null) {
            JsonNode examples = mapper.readTree(manifestJson).path("examples");
            if (!examples.isArray() || examples.isEmpty()) {
                stage8.add("manifest has no examples[] — examples[0] drives the dry-run");
            } else {
                userSchema.validate(examples.get(0)).forEach(m -> stage8.add("examples[0]: " + m.getMessage()));
            }
        }
        stages.add(new StageResult("example-dry-run", stage8.isEmpty(), stage8));

        return new Report(templateId, version, stages);
    }

    private void findExternalRefs(JsonNode node, List<String> errors) {
        if (node.isObject()) {
            JsonNode ref = node.get("$ref");
            if (ref != null && !ref.asText().startsWith("#")) {
                errors.add("external $ref not allowed: " + ref.asText());
            }
            node.properties().forEach(e -> findExternalRefs(e.getValue(), errors));
        } else if (node.isArray()) {
            node.forEach(child -> findExternalRefs(child, errors));
        }
    }

    private String readOrNull(Path file) throws Exception {
        return Files.exists(file) ? Files.readString(file) : null;
    }
}
