package app.platform.api.template;

import app.platform.common.UuidV7;
import app.platform.domain.model.Manifest;
import app.platform.templatesync.ManifestParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Interim registry hydration until the sync pipeline (3.09) lands: on startup, register every
 * templates/{id}/{version}/manifest.json found in the S3 registry that the DB doesn't know yet.
 * Idempotent (INSERT IGNORE / upsert); versions register as PUBLISHED, mirroring the
 * "S3 is a downstream cache, Git is truth" rule. Not active locally — V999 seeds there.
 */
@Component
@Profile("!local")
public class TemplateRegistryBootstrap {

    private static final Logger log = LoggerFactory.getLogger(TemplateRegistryBootstrap.class);

    private final S3Client s3;
    private final ManifestParser manifestParser;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String bucket;

    public TemplateRegistryBootstrap(
            S3Client s3,
            ManifestParser manifestParser,
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            @Value("${platform.s3.templates-bucket:}") String bucket) {
        this.s3 = s3;
        this.manifestParser = manifestParser;
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.bucket = bucket;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void hydrate() {
        if (bucket.isBlank()) {
            log.warn("No templates bucket configured — registry hydration skipped");
            return;
        }
        try {
            var keys = s3.listObjectsV2Paginator(b -> b.bucket(bucket).prefix("templates/")).contents().stream()
                    .map(o -> o.key())
                    .filter(k -> k.endsWith("/manifest.json"))
                    .toList();
            int registered = 0;
            for (String manifestKey : keys) {
                try {
                    if (register(manifestKey)) {
                        registered++;
                    }
                } catch (Exception e) {
                    log.error("Skipping invalid registry entry {}: {}", manifestKey, e.getMessage());
                }
            }
            log.info("Registry hydration: {} manifests scanned, {} newly registered", keys.size(), registered);
        } catch (Exception e) {
            // Never block startup on hydration — the admin sync endpoint can repair later.
            log.error("Registry hydration failed (continuing without): {}", e.getMessage());
        }
    }

    private boolean register(String manifestKey) {
        String json = s3.getObjectAsBytes(b -> b.bucket(bucket).key(manifestKey)).asUtf8String();
        Manifest manifest = manifestParser.parse(json);
        String base = manifestKey.substring(0, manifestKey.length() - "manifest.json".length());

        jdbc.update(
                "INSERT INTO templates (id, display_name, description, category, maturity)"
                        + " VALUES (?,?,?,?,?)"
                        + " ON DUPLICATE KEY UPDATE display_name = VALUES(display_name),"
                        + " description = VALUES(description), category = VALUES(category)",
                manifest.templateId(),
                manifest.displayName(),
                // manifest description is validated non-blank by the schema
                descriptionOf(json),
                manifest.category(),
                "beta");

        int inserted = jdbc.update(
                "INSERT IGNORE INTO template_versions"
                        + " (id, template_id, version, commit_sha, s3_key_body, s3_key_schema, s3_key_manifest,"
                        + "  status, published_at)"
                        + " VALUES (?,?,?,?,?,?,?, 'PUBLISHED', NOW(6))",
                uuidBytes(UuidV7.generate()),
                manifest.templateId(),
                manifest.version(),
                "s3-import",
                base + "template.yaml",
                base + "schema.json",
                manifestKey);
        return inserted > 0;
    }

    private String descriptionOf(String manifestJson) {
        try {
            return mapper.readTree(manifestJson).path("description").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private byte[] uuidBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
