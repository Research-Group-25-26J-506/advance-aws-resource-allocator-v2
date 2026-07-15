package app.platform.api.request;

import app.platform.domain.model.Environment;
import app.platform.domain.model.Request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class RequestDtos {

    private RequestDtos() {}

    public record CreateRequestPayload(
            @NotBlank String templateId,
            @NotNull Environment environment,
            @NotBlank @Pattern(regexp = "^[a-z]{2}-[a-z]+-\\d$") String region,
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{2,62}$") String resourceName,
            String description,
            @NotNull Map<String, Object> configuration) {}

    public record RequestDto(
            UUID id,
            String templateId,
            String environment,
            String region,
            String resourceName,
            String status,
            String requesterEmail,
            String stackId,
            String failureReason,
            Instant submittedAt) {

        public static RequestDto from(Request r) {
            return new RequestDto(
                    r.id(),
                    r.templateId(),
                    r.environment().name(),
                    r.region(),
                    r.resourceName(),
                    r.status().name(),
                    r.requesterEmail(),
                    r.stackId(),
                    r.failureReason(),
                    r.submittedAt());
        }
    }

    public record RequestEventDto(String from, String to, String reason, String source, Instant occurredAt) {}
}
