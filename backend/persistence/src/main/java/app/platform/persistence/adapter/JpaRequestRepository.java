package app.platform.persistence.adapter;

import app.platform.domain.model.Environment;
import app.platform.domain.model.Request;
import app.platform.domain.model.RequestEvent;
import app.platform.domain.model.RequestStatus;
import app.platform.domain.port.RequestRepository;
import app.platform.persistence.entity.RequestEntity;
import app.platform.persistence.entity.RequestEventEntity;
import app.platform.persistence.repo.SpringDataRepos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaRequestRepository implements RequestRepository {

    private final SpringDataRepos.Requests requests;
    private final SpringDataRepos.RequestEvents events;
    private final ObjectMapper mapper;

    public JpaRequestRepository(
            SpringDataRepos.Requests requests, SpringDataRepos.RequestEvents events, ObjectMapper mapper) {
        this.requests = requests;
        this.events = events;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public Request save(Request request) {
        requests.save(toEntity(request));
        return request;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Request> findById(UUID id) {
        return requests.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Request> findByRequester(String requesterId, int limit) {
        return requests.findByRequesterIdOrderBySubmittedAtDesc(requesterId, PageRequest.of(0, limit)).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void appendEvent(
            UUID requestId, RequestStatus from, RequestStatus to, String reason, String source, Instant at) {
        events.save(new RequestEventEntity(requestId, from == null ? null : from.name(), to.name(), reason, source, at));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequestEvent> listEvents(UUID requestId) {
        return events.findByRequestIdOrderByOccurredAtAsc(requestId).stream()
                .map(e -> new RequestEvent(
                        e.getFromStatus() == null ? null : RequestStatus.valueOf(e.getFromStatus()),
                        RequestStatus.valueOf(e.getToStatus()),
                        e.getReason(),
                        e.getSource(),
                        e.getOccurredAt()))
                .toList();
    }

    private RequestEntity toEntity(Request r) {
        try {
            return new RequestEntity(
                    r.id(),
                    r.templateVersionId(),
                    r.templateId(),
                    r.requesterId(),
                    r.requesterEmail(),
                    r.teamId(),
                    r.environment().name(),
                    r.region(),
                    r.resourceName(),
                    r.status().name(),
                    mapper.writeValueAsString(r.formData()),
                    r.idempotencyKey(),
                    r.stackId(),
                    r.failureReason(),
                    r.submittedAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("form data not serialisable", e);
        }
    }

    private Request toDomain(RequestEntity e) {
        Map<String, Object> formData;
        try {
            formData = mapper.readValue(e.getFormDataJson(), new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("corrupt form_data_json for request " + e.getId(), ex);
        }
        Request request = new Request(
                e.getId(),
                e.getTemplateVersionId(),
                e.getTemplateId(),
                e.getRequesterId(),
                e.getRequesterEmail(),
                e.getTeamId(),
                Environment.valueOf(e.getEnvironment()),
                e.getRegion(),
                e.getResourceName(),
                formData,
                e.getIdempotencyKey(),
                e.getSubmittedAt(),
                RequestStatus.valueOf(e.getStatus()));
        if (e.getStackId() != null) {
            request.recordStackId(e.getStackId());
        }
        if (e.getFailureReason() != null) {
            request.recordFailure(e.getFailureReason());
        }
        return request;
    }
}
