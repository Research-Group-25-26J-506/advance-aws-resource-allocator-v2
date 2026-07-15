package app.platform.domain.model;

import java.util.UUID;

/** A published, immutable template version. Body + schema + manifest live in S3. */
public record TemplateVersion(
        UUID id,
        String templateId,
        String version, // semver
        String commitSha,
        String s3KeyBody,
        String s3KeySchema,
        String s3KeyManifest,
        String status // DRAFT | PUBLISHED | DEPRECATED
        ) {
    public boolean published() {
        return "PUBLISHED".equals(status);
    }
}
