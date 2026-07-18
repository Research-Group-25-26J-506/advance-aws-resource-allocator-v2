package app.platform.api.request;

import app.platform.api.audit.AuditLogger;
import app.platform.common.UuidV7;
import app.platform.domain.error.NotFoundException;
import app.platform.domain.model.Environment;
import app.platform.domain.model.Request;
import app.platform.domain.model.RequestStatus;
import app.platform.domain.model.TemplateVersion;
import app.platform.domain.port.RequestRepository;
import app.platform.domain.port.TemplateRepository;
import app.platform.domain.port.WorkQueue;
import app.platform.persistence.repo.SpringDataRepos;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestService {

    private static final Logger log = LoggerFactory.getLogger(RequestService.class);

    private final RequestRepository requests;
    private final TemplateRepository templates;
    private final WorkQueue workQueue;
    private final AuditLogger audit;
    private final SpringDataRepos.Teams teams;

    @org.springframework.beans.factory.annotation.Value(
            "${platform.environments:${PLATFORM_ENVIRONMENTS:DEV,QA,STG,PROD}}")
    private String environments;

    public RequestService(
            RequestRepository requests,
            TemplateRepository templates,
            WorkQueue workQueue,
            AuditLogger audit,
            SpringDataRepos.Teams teams) {
        this.requests = requests;
        this.templates = templates;
        this.workQueue = workQueue;
        this.audit = audit;
        this.teams = teams;
    }

    @Transactional
    public Request submit(
            String actorId,
            String actorEmail,
            String templateId,
            Environment environment,
            String region,
            String resourceName,
            Map<String, Object> formData,
            String idempotencyKey) {

        TemplateVersion version = templates
                .findLatestPublished(templateId)
                .orElseThrow(() -> new NotFoundException("Published template", templateId));

        // TODO(later): resolve the requester's team from the user directory (custom:team_id claim).
        // Until then: get-or-create a default team so a fresh environment can take requests.
        UUID teamId = teams.findAll().stream()
                .findFirst()
                .orElseGet(() -> teams.save(new app.platform.persistence.entity.TeamEntity(
                        UuidV7.generate(), "default", "UNSET")))
                .getId();

        Instant now = Instant.now();
        Request request = Request.submitted(
                UuidV7.generate(),
                version.id(),
                templateId,
                actorId,
                actorEmail,
                teamId,
                environment,
                region,
                resourceName,
                formData,
                idempotencyKey,
                now);
        RequestStatus next =
                environment.requiresApproval() ? RequestStatus.PENDING_APPROVAL : RequestStatus.QUEUED;
        request.transitionTo(next);

        // Save the request row BEFORE its events — request_events has an FK on requests, and
        // Hibernate flushes inserts in persist order.
        requests.save(request);
        requests.appendEvent(request.id(), null, RequestStatus.PENDING_VALIDATION, "Submitted", "PLATFORM", now);
        requests.appendEvent(
                request.id(),
                RequestStatus.PENDING_VALIDATION,
                next,
                next == RequestStatus.PENDING_APPROVAL ? "PROD requires approval" : "Validation passed",
                "PLATFORM",
                now);

        if (request.status() == RequestStatus.QUEUED) {
            workQueue.enqueueProvision(request.id(), idempotencyKey);
        }

        audit.record(actorId, actorEmail, "REQUEST_SUBMITTED", "REQUEST", request.id().toString(),
                Map.of("templateId", templateId, "environment", environment.name(), "region", region));
        log.info("Request {} submitted for template {} in {}", request.id(), templateId, environment);
        return request;
    }

    @Transactional(readOnly = true)
    public Request get(UUID id) {
        return requests.findById(id).orElseThrow(() -> new NotFoundException("Request", id));
    }

    @Transactional(readOnly = true)
    public List<Request> listMine(String actorId, int limit) {
        return requests.findByRequester(actorId, limit);
    }

    @Transactional(readOnly = true)
    public List<app.platform.domain.model.RequestEvent> listEvents(UUID id) {
        get(id); // 404 if unknown
        return requests.listEvents(id);
    }

    /**
     * Resource promotion: re-provisions the same template + configuration at the next
     * environment (DEV -> STG -> PROD). PROD promotions park in the approvals inbox like any
     * other PROD request. The source resource is untouched.
     */
    @Transactional
    public Request promote(String actorId, String actorEmail, UUID sourceId, String idempotencyKey) {
        Request source = get(sourceId);
        if (source.status() != RequestStatus.CREATE_COMPLETE && source.status() != RequestStatus.UPDATE_COMPLETE) {
            throw new app.platform.domain.error.IllegalTransitionException(
                    sourceId, source.status(), RequestStatus.QUEUED);
        }
        // Promotion order comes from configuration (SSM-backed), not code — environments are
        // added/removed without a release.
        java.util.List<String> chain = java.util.Arrays.asList(environments.split(","));
        int index = chain.indexOf(source.environment().name());
        if (index < 0 || index >= chain.size() - 1) {
            throw new app.platform.domain.error.NotFoundException(
                    "Environment above", source.environment());
        }
        Environment next = Environment.valueOf(chain.get(index + 1).trim());
        Request promoted = submit(
                actorId,
                actorEmail,
                source.templateId(),
                next,
                source.region(),
                source.resourceName(),
                source.formData(),
                idempotencyKey);
        audit.record(actorId, actorEmail, "RESOURCE_PROMOTED", "REQUEST", promoted.id().toString(),
                Map.of("from", source.environment().name(), "to", next.name(),
                        "sourceRequestId", sourceId.toString()));
        return promoted;
    }

    @Transactional
    public Request approve(String actorId, UUID id) {
        Request request = get(id);
        // Separation of duties: the requester cannot approve their own PROD request, regardless
        // of the roles they hold (Phase A security).
        if (request.requesterId().equals(actorId)) {
            throw new app.platform.domain.error.SelfApprovalException(id);
        }
        request.transitionTo(RequestStatus.QUEUED); // legal only from PENDING_APPROVAL
        requests.save(request);
        requests.appendEvent(id, RequestStatus.PENDING_APPROVAL, RequestStatus.QUEUED,
                "Approved by " + actorId, "PLATFORM", Instant.now());
        workQueue.enqueueProvision(id, request.idempotencyKey());
        audit.record(actorId, actorId, "APPROVAL_GRANTED", "REQUEST", id.toString(), Map.of());
        return request;
    }

    @Transactional
    public Request reject(String actorId, UUID id, String reason) {
        Request request = get(id);
        request.recordFailure(reason);
        request.transitionTo(RequestStatus.REJECTED);
        requests.save(request);
        requests.appendEvent(id, RequestStatus.PENDING_APPROVAL, RequestStatus.REJECTED,
                "Rejected by " + actorId + ": " + reason, "PLATFORM", Instant.now());
        audit.record(actorId, actorId, "APPROVAL_REJECTED", "REQUEST", id.toString(), Map.of("reason", reason));
        return request;
    }

    @Transactional
    public Request retry(String actorId, UUID id) {
        Request request = get(id);
        RequestStatus from = request.status();
        request.transitionTo(RequestStatus.QUEUED); // legal only from failure states
        requests.save(request);
        requests.appendEvent(id, from, RequestStatus.QUEUED, "Retried by " + actorId, "PLATFORM", Instant.now());
        workQueue.enqueueProvision(id, request.idempotencyKey());
        audit.record(actorId, null, "REQUEST_RETRIED", "REQUEST", id.toString(), Map.of());
        return request;
    }

    /**
     * Re-drive a stuck in-progress request by re-enqueuing its pending operation. No status
     * transition — the worker handlers are idempotent on their in-progress status. Recovers
     * requests whose SQS message was lost (e.g. worker replaced mid-flight during a deploy).
     */
    @Transactional
    public Request reconcile(String actorId, UUID id) {
        Request request = get(id);
        switch (request.status()) {
            case DELETE_IN_PROGRESS -> workQueue.enqueueDelete(id, request.idempotencyKey());
            case QUEUED, CREATE_IN_PROGRESS -> workQueue.enqueueProvision(id, request.idempotencyKey());
            default -> throw new app.platform.domain.error.IllegalTransitionException(
                    id, request.status(), request.status());
        }
        requests.appendEvent(id, request.status(), request.status(),
                "Re-driven by " + actorId + " (reconcile)", "PLATFORM", Instant.now());
        audit.record(actorId, actorId, "REQUEST_RECONCILED", "REQUEST", id.toString(),
                Map.of("status", request.status().name()));
        return request;
    }

    @Transactional
    public Request delete(String actorId, UUID id) {
        Request request = get(id);
        RequestStatus from = request.status();
        request.transitionTo(RequestStatus.DELETE_IN_PROGRESS);
        requests.save(request);
        requests.appendEvent(
                id, from, RequestStatus.DELETE_IN_PROGRESS, "Delete requested by " + actorId, "PLATFORM", Instant.now());
        workQueue.enqueueDelete(id, request.idempotencyKey());
        audit.record(actorId, null, "REQUEST_DELETED", "REQUEST", id.toString(), Map.of());
        return request;
    }
}
