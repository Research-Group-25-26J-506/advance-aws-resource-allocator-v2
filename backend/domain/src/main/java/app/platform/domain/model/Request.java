package app.platform.domain.model;

import app.platform.domain.error.IllegalTransitionException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One user submission bound to one template version. Aurora is the record of "what was asked
 * for"; CloudFormation remains the record of what exists.
 */
public class Request {

    private final UUID id;
    private final UUID templateVersionId;
    private final String templateId;
    private final String requesterId;
    private final String requesterEmail;
    private final UUID teamId;
    private final Environment environment;
    private final String region;
    private final String resourceName;
    private final Map<String, Object> formData;
    private final Map<String, String> customTags;
    private final String idempotencyKey;
    private final Instant submittedAt;
    private RequestStatus status;
    private String stackId;
    private String failureReason;

    public Request(
            UUID id,
            UUID templateVersionId,
            String templateId,
            String requesterId,
            String requesterEmail,
            UUID teamId,
            Environment environment,
            String region,
            String resourceName,
            Map<String, Object> formData,
            Map<String, String> customTags,
            String idempotencyKey,
            Instant submittedAt,
            RequestStatus status) {
        this.id = id;
        this.templateVersionId = templateVersionId;
        this.templateId = templateId;
        this.requesterId = requesterId;
        this.requesterEmail = requesterEmail;
        this.teamId = teamId;
        this.environment = environment;
        this.region = region;
        this.resourceName = resourceName;
        this.formData = Map.copyOf(formData);
        this.customTags = customTags == null ? Map.of() : Map.copyOf(customTags);
        this.idempotencyKey = idempotencyKey;
        this.submittedAt = submittedAt;
        this.status = status;
    }

    public static Request submitted(
            UUID id,
            UUID templateVersionId,
            String templateId,
            String requesterId,
            String requesterEmail,
            UUID teamId,
            Environment environment,
            String region,
            String resourceName,
            Map<String, Object> formData,
            Map<String, String> customTags,
            String idempotencyKey,
            Instant now) {
        return new Request(
                id,
                templateVersionId,
                templateId,
                requesterId,
                requesterEmail,
                teamId,
                environment,
                region,
                resourceName,
                formData,
                customTags,
                idempotencyKey,
                now,
                RequestStatus.PENDING_VALIDATION);
    }

    /** The single mutation path — every transition is validated against the lifecycle map. */
    public void transitionTo(RequestStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalTransitionException(id, status, next);
        }
        this.status = next;
    }

    public void recordStackId(String stackId) {
        this.stackId = stackId;
    }

    public void recordFailure(String reason) {
        this.failureReason = reason;
    }

    public UUID id() {
        return id;
    }

    public UUID templateVersionId() {
        return templateVersionId;
    }

    public String templateId() {
        return templateId;
    }

    public String requesterId() {
        return requesterId;
    }

    public String requesterEmail() {
        return requesterEmail;
    }

    public UUID teamId() {
        return teamId;
    }

    public Environment environment() {
        return environment;
    }

    public String region() {
        return region;
    }

    public String resourceName() {
        return resourceName;
    }

    public Map<String, Object> formData() {
        return formData;
    }

    /** User-supplied extra tags (TagEditor). Applied at provision, but mandatory tags always win. */
    public Map<String, String> customTags() {
        return customTags;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public RequestStatus status() {
        return status;
    }

    public String stackId() {
        return stackId;
    }

    public String failureReason() {
        return failureReason;
    }
}
