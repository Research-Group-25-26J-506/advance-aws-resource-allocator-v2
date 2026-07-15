package app.platform.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Parsed template manifest (schema: templatesync/manifest-v1.json). Parsing happens in an
 * adapter; the domain only sees this typed shape.
 */
public record Manifest(
        String templateId,
        String version,
        String displayName,
        String category,
        RenderMode renderMode,
        // schema field path (dot notation, e.g. "bucket.versioning") -> CFN parameter name
        Map<String, String> parameterMap,
        String executionRoleArn,
        List<String> cfnCapabilities,
        Map<String, Map<String, Object>> defaultsByEnv,
        String estimatedMonthlyCost) {

    public enum RenderMode {
        PARAMETER,
        JINJA
    }
}
