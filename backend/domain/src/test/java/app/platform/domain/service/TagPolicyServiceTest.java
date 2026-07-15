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

    @Test
    void computesAllSevenMandatoryTags() {
        var request = Request.submitted(
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
                "0190a1b2-0000-7000-8000-000000000000",
                Instant.now());
        var team = new Team(UUID.randomUUID(), "payments", "CC-1234");
        var version =
                new TemplateVersion(UUID.randomUUID(), "s3-bucket", "1.0.0", "abc1234", "b", "s", "m", "PUBLISHED");

        var tags = new TagPolicyService().mandatoryTags(request, team, version, "data-lake");

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
}
