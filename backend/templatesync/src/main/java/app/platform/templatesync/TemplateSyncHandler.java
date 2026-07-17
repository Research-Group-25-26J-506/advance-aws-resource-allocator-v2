package app.platform.templatesync;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Template sync (3.09), scoped: lock -> fetch tarball -> walk templates/{id}/{version}/ ->
 * diff vs DB -> fail-fast on immutability violations -> validate all added -> (DRY_RUN stops)
 * -> upload S3 THEN insert DB rows (crash between the two leaves only GC-able S3 objects).
 * Every stage appends a sync_events row (powers the 2.10 timeline).
 */
@Service
public class TemplateSyncHandler {

    private static final Logger log = LoggerFactory.getLogger(TemplateSyncHandler.class);

    private final JdbcTemplate jdbc;
    private final S3Client s3;
    private final DistributedLockService locks;
    private final TarballFetcher fetcher;
    private final TemplateValidator validator;
    private final ManifestParser manifestParser;
    private final ObjectMapper mapper;
    private final String bucket;
    private final String repo;
    private final String branch;

    public TemplateSyncHandler(
            JdbcTemplate jdbc,
            S3Client s3,
            DistributedLockService locks,
            TarballFetcher fetcher,
            TemplateValidator validator,
            ManifestParser manifestParser,
            ObjectMapper mapper,
            @Value("${platform.s3.templates-bucket:}") String bucket,
            @Value("${platform.templates.repo:${PLATFORM_TEMPLATES_REPO:}}") String repo,
            @Value("${platform.templates.branch:${PLATFORM_TEMPLATES_BRANCH:develop}}") String branch) {
        this.jdbc = jdbc;
        this.s3 = s3;
        this.locks = locks;
        this.fetcher = fetcher;
        this.validator = validator;
        this.manifestParser = manifestParser;
        this.mapper = mapper;
        this.bucket = bucket;
        this.repo = repo;
        this.branch = branch;
    }

    private record Found(String templateId, String version, Path dir) {}

    public void handle(UUID syncId) {
        String mode = jdbc.queryForObject(
                "SELECT mode FROM template_syncs WHERE id = ?", String.class, uuidBytes(syncId));
        try (DistributedLockService.Lock ignored = locks.acquire("template-sync", Duration.ofMinutes(5))) {
            transition(syncId, "SYNC_PENDING", "SYNC_VALIDATING", "Lock acquired; fetching " + repo + "@" + branch);
            try (TarballFetcher.ExtractedRepo extracted = fetcher.fetch(repo, branch)) {
                // GitHub tarballs wrap everything in a single {org}-{repo}-{sha}/ root dir
                Path root;
                try (Stream<Path> children = Files.list(extracted.baseDir())) {
                    root = children.findFirst().orElseThrow();
                }
                Path templatesDir = root.resolve("templates");
                List<Found> found = walk(templatesDir);
                event(syncId, "SYNC_VALIDATING", "Found " + found.size() + " template version(s) in repo");

                List<Found> added = new ArrayList<>();
                List<String> violations = new ArrayList<>();
                for (Found f : found) {
                    Integer known = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM template_versions WHERE template_id = ? AND version = ?",
                            Integer.class, f.templateId(), f.version());
                    if (known != null && known > 0) {
                        // PUBLISHED versions are immutable: same id+version must be byte-identical.
                        // Scoped check: existence == unchanged (deep diff arrives with 5.03).
                        continue;
                    }
                    added.add(f);
                }
                if (!violations.isEmpty()) {
                    fail(syncId, "Immutability violations: " + violations);
                    return;
                }

                List<TemplateValidator.Report> failures = new ArrayList<>();
                for (Found f : added) {
                    TemplateValidator.Report report = validator.validate(f.templateId(), f.version(), f.dir());
                    event(syncId, "SYNC_VALIDATING", "Validated %s/%s: %s".formatted(
                            f.templateId(), f.version(), report.passed() ? "PASS" : "FAIL " + report.stages()));
                    if (!report.passed()) {
                        failures.add(report);
                    }
                }
                if (!failures.isEmpty()) {
                    // Atomic: any failure = zero uploads
                    fail(syncId, failures.size() + " template(s) failed validation — nothing published");
                    return;
                }
                if ("DRY_RUN".equals(mode)) {
                    complete(syncId, "DRY_RUN: %d new version(s) would publish, %d already known"
                            .formatted(added.size(), found.size() - added.size()));
                    return;
                }

                transition(syncId, "SYNC_VALIDATING", "SYNC_UPLOADING", "Publishing " + added.size() + " version(s)");
                for (Found f : added) {
                    publish(f);
                    event(syncId, "SYNC_UPLOADING", "Published " + f.templateId() + "/" + f.version());
                }
                complete(syncId, "%d published, %d unchanged".formatted(added.size(), found.size() - added.size()));
            }
        } catch (DistributedLockService.LockTimeoutException e) {
            fail(syncId, e.getMessage());
        } catch (Exception e) {
            log.error("Sync {} failed", syncId, e);
            fail(syncId, e.getMessage());
        }
    }

    private List<Found> walk(Path templatesDir) throws Exception {
        List<Found> found = new ArrayList<>();
        if (!Files.isDirectory(templatesDir)) {
            return found;
        }
        try (Stream<Path> ids = Files.list(templatesDir)) {
            for (Path idDir : ids.filter(Files::isDirectory).toList()) {
                try (Stream<Path> versions = Files.list(idDir)) {
                    for (Path versionDir : versions.filter(Files::isDirectory).toList()) {
                        found.add(new Found(idDir.getFileName().toString(),
                                versionDir.getFileName().toString(), versionDir));
                    }
                }
            }
        }
        return found;
    }

    private void publish(Found f) throws Exception {
        String prefix = "templates/%s/%s/".formatted(f.templateId(), f.version());
        for (String file : List.of("template.yaml", "schema.json", "manifest.json")) {
            s3.putObject(b -> b.bucket(bucket).key(prefix + file),
                    RequestBody.fromBytes(Files.readAllBytes(f.dir().resolve(file))));
        }
        var manifest = manifestParser.parse(Files.readString(f.dir().resolve("manifest.json")));
        String description = mapper.readTree(Files.readString(f.dir().resolve("manifest.json")))
                .path("description").asText("");
        jdbc.update(
                "INSERT INTO templates (id, display_name, description, category, maturity) VALUES (?,?,?,?,?)"
                        + " ON DUPLICATE KEY UPDATE display_name=VALUES(display_name),"
                        + " description=VALUES(description), category=VALUES(category)",
                manifest.templateId(), manifest.displayName(), description, manifest.category(), "beta");
        jdbc.update(
                "INSERT IGNORE INTO template_versions (id, template_id, version, commit_sha,"
                        + " s3_key_body, s3_key_schema, s3_key_manifest, status, published_at)"
                        + " VALUES (?,?,?,?,?,?,?, 'PUBLISHED', NOW(6))",
                uuidBytes(UUID.randomUUID()), manifest.templateId(), manifest.version(), branch,
                prefix + "template.yaml", prefix + "schema.json", prefix + "manifest.json");
    }

    private void transition(UUID syncId, String from, String to, String detail) {
        jdbc.update("UPDATE template_syncs SET status = ? WHERE id = ? AND status = ?",
                to, uuidBytes(syncId), from);
        jdbc.update("INSERT INTO sync_events (sync_id, from_status, to_status, detail, occurred_at)"
                + " VALUES (?,?,?,?, NOW(6))", uuidBytes(syncId), from, to, detail);
    }

    private void event(UUID syncId, String status, String detail) {
        jdbc.update("INSERT INTO sync_events (sync_id, from_status, to_status, detail, occurred_at)"
                + " VALUES (?,?,?,?, NOW(6))", uuidBytes(syncId), status, status, detail);
    }

    private void complete(UUID syncId, String summary) {
        jdbc.update("UPDATE template_syncs SET status = 'SYNC_COMPLETE', completed_at = NOW(6),"
                + " summary_json = ? WHERE id = ?", jsonOf(summary), uuidBytes(syncId));
        jdbc.update("INSERT INTO sync_events (sync_id, from_status, to_status, detail, occurred_at)"
                + " VALUES (?, NULL, 'SYNC_COMPLETE', ?, NOW(6))", uuidBytes(syncId), summary);
        log.info("Sync {} complete: {}", syncId, summary);
    }

    private void fail(UUID syncId, String reason) {
        jdbc.update("UPDATE template_syncs SET status = 'SYNC_FAILED', completed_at = NOW(6),"
                + " error_json = ? WHERE id = ?", jsonOf(reason), uuidBytes(syncId));
        jdbc.update("INSERT INTO sync_events (sync_id, from_status, to_status, detail, occurred_at)"
                + " VALUES (?, NULL, 'SYNC_FAILED', ?, NOW(6))", uuidBytes(syncId), reason);
        log.warn("Sync {} failed: {}", syncId, reason);
    }

    private String jsonOf(String message) {
        try {
            return mapper.writeValueAsString(Map.of("message", message));
        } catch (Exception e) {
            return "{\"message\":\"unserialisable\"}";
        }
    }

    public static byte[] uuidBytes(UUID uuid) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
