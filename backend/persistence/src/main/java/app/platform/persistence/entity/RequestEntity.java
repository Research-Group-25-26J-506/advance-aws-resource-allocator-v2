package app.platform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "requests")
public class RequestEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "template_version_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID templateVersionId;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(name = "requester_id", nullable = false)
    private String requesterId;

    @Column(name = "requester_email", nullable = false)
    private String requesterEmail;

    @Column(name = "team_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID teamId;

    @Column(nullable = false)
    private String environment;

    @Column(nullable = false)
    private String region;

    @Column(name = "resource_name", nullable = false)
    private String resourceName;

    @Column(nullable = false)
    private String status;

    @Column(name = "form_data_json", nullable = false, columnDefinition = "JSON")
    private String formDataJson;

    @Column(name = "custom_tags_json", columnDefinition = "JSON")
    private String customTagsJson;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "stack_id")
    private String stackId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected RequestEntity() {}

    public RequestEntity(
            UUID id,
            UUID templateVersionId,
            String templateId,
            String requesterId,
            String requesterEmail,
            UUID teamId,
            String environment,
            String region,
            String resourceName,
            String status,
            String formDataJson,
            String customTagsJson,
            String idempotencyKey,
            String stackId,
            String failureReason,
            Instant submittedAt) {
        this.id = id;
        this.templateVersionId = templateVersionId;
        this.templateId = templateId;
        this.requesterId = requesterId;
        this.requesterEmail = requesterEmail;
        this.teamId = teamId;
        this.environment = environment;
        this.region = region;
        this.resourceName = resourceName;
        this.status = status;
        this.formDataJson = formDataJson;
        this.customTagsJson = customTagsJson;
        this.idempotencyKey = idempotencyKey;
        this.stackId = stackId;
        this.failureReason = failureReason;
        this.submittedAt = submittedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTemplateVersionId() {
        return templateVersionId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getRegion() {
        return region;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getStatus() {
        return status;
    }

    public String getFormDataJson() {
        return formDataJson;
    }

    public String getCustomTagsJson() {
        return customTagsJson;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getStackId() {
        return stackId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setStackId(String stackId) {
        this.stackId = stackId;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
