package app.platform.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.platform.domain.error.UnmappedFieldException;
import app.platform.domain.model.Manifest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer((body, ctx) -> body + "|rendered");

    private Manifest manifest(Map<String, String> parameterMap, Manifest.RenderMode mode) {
        return new Manifest(
                "s3-bucket",
                "1.0.0",
                "S3 Bucket",
                "Storage",
                mode,
                parameterMap,
                "arn:aws:iam::123:role/platform-exec-s3-bucket",
                List.of(),
                Map.of(),
                "1.00");
    }

    @Test
    void mapsSchemaPathsToCfnParameters() {
        var m = manifest(
                Map.of("bucketName", "BucketName", "versioning.enabled", "VersioningEnabled"),
                Manifest.RenderMode.PARAMETER);
        var form = Map.<String, Object>of("bucketName", "my-data", "versioning", Map.of("enabled", true));

        var result = renderer.render(m, form, "BODY");

        assertThat(result.parameters())
                .containsEntry("BucketName", "my-data")
                .containsEntry("VersioningEnabled", "true");
        assertThat(result.body()).isEqualTo("BODY");
    }

    @Test
    void unmappedFieldIsHardError() {
        var m = manifest(Map.of("bucketName", "BucketName"), Manifest.RenderMode.PARAMETER);
        var form = Map.<String, Object>of("bucketName", "x", "rogueField", "y");

        assertThatThrownBy(() -> renderer.render(m, form, "BODY")).isInstanceOf(UnmappedFieldException.class);
    }

    @Test
    void jinjaModeDelegatesToSandboxedTemplater() {
        var m = manifest(Map.of(), Manifest.RenderMode.JINJA);

        var result = renderer.render(m, Map.of("k", "v"), "BODY");

        assertThat(result.body()).isEqualTo("BODY|rendered");
        assertThat(result.parameters()).isEmpty();
    }
}
