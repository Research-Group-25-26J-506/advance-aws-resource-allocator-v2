package app.platform.api.builds;

import app.platform.api.audit.AuditLogger;
import app.platform.common.UuidV7;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.codebuild.CodeBuildClient;
import software.amazon.awssdk.services.codebuild.model.EnvironmentVariable;
import software.amazon.awssdk.services.ecr.EcrClient;
import software.amazon.awssdk.services.ecr.model.EcrException;
import software.amazon.awssdk.services.ecr.model.ImageScanningConfiguration;
import software.amazon.awssdk.services.ecr.model.Tag;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * Source-to-image builds. The platform triggers one CodeBuild project with per-build overrides
 * (repo URL, ref, image tag, target ECR repo); it clones the repo, builds the image, and pushes to
 * ECR — nobody runs Docker locally. Only repos in an allowlisted GitHub org may be built.
 *
 * <p>Isolation: each service pushes to its OWN ECR repository ({@code platform-svc-<service>}),
 * created on demand with a keep-last-N lifecycle policy, so user images never share the platform
 * control-plane repo. Every build returns the full, copy-paste-ready image URI.
 *
 * <p>NOTE: this lives in package {@code builds} (not {@code build}) on purpose — a {@code build/}
 * directory is git-ignored (Gradle output), which previously kept this file out of the repo.
 */
@RestController
@RequestMapping("/api/v1/builds")
public class BuildsController {

    private static final Logger log = LoggerFactory.getLogger(BuildsController.class);
    private static final Pattern GITHUB_URL =
            Pattern.compile("^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$");
    private static final Pattern SERVICE_NAME = Pattern.compile("^[a-z][a-z0-9-]{1,40}$");
    private static final String LIFECYCLE_POLICY =
            "{\"rules\":[{\"rulePriority\":1,\"description\":\"keep last 20 images\","
                    + "\"selection\":{\"tagStatus\":\"any\",\"countType\":\"imageCountMoreThan\",\"countNumber\":20},"
                    + "\"action\":{\"type\":\"expire\"}}]}";

    private final CodeBuildClient codeBuild;
    private final EcrClient ecr;
    private final StsClient sts;
    private final JdbcTemplate jdbc;
    private final AuditLogger audit;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String project;
    private final String region;
    private final Set<String> allowedOrgs;
    private volatile String accountId; // resolved once via STS

    public BuildsController(
            CodeBuildClient codeBuild,
            EcrClient ecr,
            StsClient sts,
            JdbcTemplate jdbc,
            AuditLogger audit,
            @Value("${platform.builds.project:${PLATFORM_BUILD_PROJECT:platform-dev-image-builder}}") String project,
            @Value("${platform.aws.region:us-east-1}") String region,
            @Value("${platform.builds.allowed-orgs:${PLATFORM_BUILD_ALLOWED_ORGS:Research-Group-25-26J-506}}")
                    String allowedOrgs) {
        this.codeBuild = codeBuild;
        this.ecr = ecr;
        this.sts = sts;
        this.jdbc = jdbc;
        this.audit = audit;
        this.project = project;
        this.region = region;
        this.allowedOrgs = Arrays.stream(allowedOrgs.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public record TriggerPayload(String repo, String ref, String serviceName) {}

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT BIN_TO_UUID(id) AS id, service_name, repo, git_ref, image_tag, codebuild_id, status,"
                        + " started_at FROM builds ORDER BY started_at DESC LIMIT 50",
                (rs, i) -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    String service = rs.getString("service_name");
                    String tag = rs.getString("image_tag");
                    m.put("id", rs.getString("id"));
                    m.put("serviceName", service);
                    m.put("repo", rs.getString("repo"));
                    m.put("ref", rs.getString("git_ref"));
                    m.put("imageTag", tag);
                    m.put("imageUri", imageUri(service, tag));
                    m.put("codebuildId", rs.getString("codebuild_id"));
                    m.put("status", rs.getString("status"));
                    m.put("startedAt", String.valueOf(rs.getTimestamp("started_at").toInstant()));
                    return m;
                });
        refreshInProgress(rows);
        rows.forEach(m -> m.remove("codebuildId"));
        return rows;
    }

    /** List the branches of an allowlisted GitHub repo so the UI can offer a picker. Public repos
     * only (no token); private repos need the GitHub App and return an empty list here. */
    @GetMapping("/branches")
    @PreAuthorize("isAuthenticated()")
    public List<String> branches(@RequestParam String repo) {
        Matcher m = GITHUB_URL.matcher(repo == null ? "" : repo.trim());
        if (!m.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not a GitHub repository URL");
        }
        if (!allowedOrgs.contains(m.group(1))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Org '" + m.group(1) + "' is not allowlisted");
        }
        String api = "https://api.github.com/repos/" + m.group(1) + "/" + m.group(2) + "/branches?per_page=100";
        try {
            HttpResponse<String> resp = http.send(
                    HttpRequest.newBuilder(URI.create(api))
                            .header("Accept", "application/vnd.github+json")
                            .header("User-Agent", "aws-self-service-platform")
                            .timeout(Duration.ofSeconds(8))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 404 || resp.statusCode() == 403) {
                // private repo or rate-limited without a token — the UI falls back to a free-text ref
                return List.of();
            }
            if (resp.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub API returned " + resp.statusCode());
            }
            List<String> branches = new ArrayList<>();
            mapper.readTree(resp.body()).forEach(node -> branches.add(node.get("name").asText()));
            return branches;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not list branches for {}: {}", api, e.getMessage());
            return List.of(); // never block the build form on a branch lookup
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','PLATFORM_ADMIN')")
    public Map<String, Object> trigger(@RequestBody TriggerPayload payload, Authentication auth) {
        String repo = payload.repo() == null ? "" : payload.repo().trim();
        String ref = payload.ref() == null || payload.ref().isBlank() ? "main" : payload.ref().trim();
        Matcher m = GITHUB_URL.matcher(repo);
        if (!m.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Repository must be a GitHub URL like https://github.com/<org>/<repo>");
        }
        String org = m.group(1);
        if (!allowedOrgs.contains(org)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Org '" + org + "' is not allowlisted for builds. Allowed: " + allowedOrgs);
        }
        String serviceName = payload.serviceName() == null || payload.serviceName().isBlank()
                ? m.group(2).toLowerCase(Locale.ROOT)
                : payload.serviceName().trim();
        if (!SERVICE_NAME.matcher(serviceName).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Service name must be lowercase letters, digits, hyphens (2-41 chars)");
        }

        String repoName = ensureRepo(serviceName); // per-service ECR repo, created on demand
        var id = UuidV7.generate();
        String imageTag = serviceName + "-" + id.toString().replace("-", "").substring(24);
        String codebuildId;
        try {
            codebuildId = codeBuild.startBuild(b -> b.projectName(project)
                            .environmentVariablesOverride(
                                    EnvironmentVariable.builder().name("REPO_URL").value(repo).build(),
                                    EnvironmentVariable.builder().name("REPO_REF").value(ref).build(),
                                    EnvironmentVariable.builder().name("SERVICE_NAME").value(serviceName).build(),
                                    EnvironmentVariable.builder().name("IMAGE_TAG").value(imageTag).build(),
                                    EnvironmentVariable.builder().name("ECR_REPO").value(repoName).build()))
                    .build()
                    .id();
        } catch (Exception e) {
            log.error("StartBuild failed for {} ({})", repo, project, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not start the build: " + e.getMessage());
        }

        jdbc.update(
                "INSERT INTO builds (id, service_name, repo, git_ref, image_tag, codebuild_id, status, actor_id)"
                        + " VALUES (UUID_TO_BIN(?), ?, ?, ?, ?, ?, 'IN_PROGRESS', ?)",
                id.toString(), serviceName, repo, ref, imageTag, codebuildId, auth.getName());
        String uri = imageUri(serviceName, imageTag);
        audit.record(auth.getName(), auth.getName(), "BUILD_TRIGGERED", "BUILD", id.toString(),
                Map.of("repo", repo, "ref", ref, "imageUri", uri));
        log.info("Build {} started ({}) for {}@{} -> {}", id, codebuildId, repo, ref, uri);
        return Map.of("id", id.toString(), "imageTag", imageTag, "imageUri", uri);
    }

    /** Per-service ECR repo, created idempotently with scan-on-push + a keep-last-20 lifecycle. */
    private String ensureRepo(String serviceName) {
        String repoName = "platform-svc-" + serviceName;
        try {
            ecr.createRepository(b -> b.repositoryName(repoName)
                    .imageScanningConfiguration(ImageScanningConfiguration.builder().scanOnPush(true).build())
                    .tags(
                            Tag.builder().key("ManagedBy").value("Platform").build(),
                            Tag.builder().key("Service").value(serviceName).build()));
            ecr.putLifecyclePolicy(b -> b.repositoryName(repoName).lifecyclePolicyText(LIFECYCLE_POLICY));
            log.info("Created ECR repository {}", repoName);
        } catch (EcrException e) {
            if (e.awsErrorDetails() != null
                    && "RepositoryAlreadyExistsException".equals(e.awsErrorDetails().errorCode())) {
                return repoName; // already there — nothing to do
            }
            log.warn("Could not ensure ECR repo {} (push will surface the error): {}", repoName, e.getMessage());
        }
        return repoName;
    }

    private String imageUri(String serviceName, String imageTag) {
        return registry() + "/platform-svc-" + serviceName + ":" + imageTag;
    }

    private String registry() {
        String acct = accountId;
        if (acct == null) {
            try {
                acct = sts.getCallerIdentity().account();
            } catch (Exception e) {
                acct = "ACCOUNT"; // degraded: still return a shaped URI
            }
            accountId = acct;
        }
        return acct + ".dkr.ecr." + region + ".amazonaws.com";
    }

    /** Refresh any IN_PROGRESS rows from CodeBuild so the list settles without an event pipeline. */
    private void refreshInProgress(List<Map<String, Object>> rows) {
        List<String> ids = rows.stream()
                .filter(r -> "IN_PROGRESS".equals(r.get("status")) && r.get("codebuildId") != null)
                .map(r -> (String) r.get("codebuildId"))
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        try {
            var byId = codeBuild.batchGetBuilds(b -> b.ids(ids)).builds().stream()
                    .collect(Collectors.toMap(bd -> bd.id(), bd -> bd));
            for (Map<String, Object> row : rows) {
                var bd = byId.get((String) row.get("codebuildId"));
                if (bd == null) {
                    continue;
                }
                String fresh = mapStatus(bd.buildStatusAsString());
                // The build tags the image with the real commit SHA and exports it back here; adopt
                // it so the recorded image tag/URI is the actual pushed image, not the initial id.
                String exportedTag = bd.exportedEnvironmentVariables().stream()
                        .filter(e -> "IMAGE_TAG".equals(e.name()))
                        .map(e -> e.value())
                        .findFirst()
                        .orElse(null);
                boolean statusChanged = !fresh.equals(row.get("status"));
                boolean tagChanged = exportedTag != null && !exportedTag.equals(row.get("imageTag"));
                if (!statusChanged && !tagChanged) {
                    continue;
                }
                String completed = "IN_PROGRESS".equals(fresh) ? "completed_at" : "NOW(6)";
                if (tagChanged) {
                    jdbc.update("UPDATE builds SET status = ?, image_tag = ?, completed_at = " + completed
                            + " WHERE id = UUID_TO_BIN(?)", fresh, exportedTag, (String) row.get("id"));
                    row.put("imageTag", exportedTag);
                    row.put("imageUri", imageUri((String) row.get("serviceName"), exportedTag));
                } else {
                    jdbc.update("UPDATE builds SET status = ?, completed_at = " + completed
                            + " WHERE id = UUID_TO_BIN(?)", fresh, (String) row.get("id"));
                }
                row.put("status", fresh);
            }
        } catch (Exception e) {
            log.warn("Could not refresh build statuses: {}", e.getMessage());
        }
    }

    private static String mapStatus(String codeBuildStatus) {
        if (codeBuildStatus == null) {
            return "IN_PROGRESS";
        }
        return switch (codeBuildStatus) {
            case "SUCCEEDED" -> "SUCCEEDED";
            case "IN_PROGRESS" -> "IN_PROGRESS";
            default -> "FAILED"; // FAILED | FAULT | TIMED_OUT | STOPPED
        };
    }
}
