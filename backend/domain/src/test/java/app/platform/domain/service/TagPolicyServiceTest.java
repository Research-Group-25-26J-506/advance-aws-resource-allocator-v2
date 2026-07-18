package app.platform.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.platform.domain.model.Environment;
import app.platform.domain.model.Request;
import app.platform.domain.model.Tag;
import app.platform.domain.model.Team;
import app.platform.domain.model.TemplateVersion;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TagPolicyServiceTest {

    private static Request request(Map<String, String> customTags) {
        return Request.submitted(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "s3-bucket",
                "user-1",
                "dev@example.com",
                UUID.randomUUID(),
                Environment.DEV,
                "us-east-1",
                "my-bucket",
                Map.of(),
                customTags,
                "0190a1b2-0000-7000-8000-000000000000",
                Instant.now());
    }

    private static final Team TEAM = new Team(UUID.randomUUID(), "payments", "CC-1234");
    private static final TemplateVersion VERSION =
            new TemplateVersion(UUID.randomUUID(), "s3-bucket", "1.0.0", "abc1234", "b", "s", "m", "PUBLISHED");

    @Test
    void computesAllSevenMandatoryTags() {
        var tags = new TagPolicyService().mandatoryTags(request(Map.of()), TEAM, VERSION, "data-lake");

        assertThat(tags)
                .contains(
                        new Tag("Owner", "dev@example.com"),
                        new Tag("CostCenter", "CC-1234"),
                        new Tag("Environment", "DEV"),
                        new Tag("Application", "data-lake"),
                        new Tag("ManagedBy", "Platform"),
                        new Tag("SourceCommitSha", "abc1234"))
                .anyMatch(t -> t.key().equals("PlatformRequestId"));
    }

    @Test
    void appendsCustomTagsButMandatoryAlwaysWins() {
        // "Environment" and "aws:foo" must be dropped; "Team" and "CostOwner" kept.
        var custom = new java.util.LinkedHashMap<String, String>();
        custom.put("Team", "Payments");
        custom.put("Environment", "PROD"); // collides with a mandatory key -> ignored
        custom.put("aws:reserved", "x"); // reserved prefix -> ignored
        custom.put("CostOwner", "alice");

        var tags = new TagPolicyService().mandatoryTags(request(custom), TEAM, VERSION, "data-lake");

        assertThat(tags).contains(new Tag("Team", "Payments"), new Tag("CostOwner", "alice"));
        assertThat(tags).contains(new Tag("Environment", "DEV")); // mandatory value preserved
        assertThat(tags).noneMatch(t -> t.value().equals("PROD"));
        assertThat(tags).noneMatch(t -> t.key().startsWith("aws:"));
    }
}
