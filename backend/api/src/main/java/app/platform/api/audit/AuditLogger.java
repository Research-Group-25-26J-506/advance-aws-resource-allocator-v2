package app.platform.api.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Audit log entry for every state-changing action (definition of done, 9.2). Append-only. */
@Component
public class AuditLogger {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditLogger(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void record(
            String actorId, String actorEmail, String action, String subjectType, String subjectId,
            Map<String, Object> detail) {
        String detailJson;
        try {
            detailJson = detail == null || detail.isEmpty() ? null : mapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            detailJson = null;
        }
        String traceId = Span.current().getSpanContext().isValid()
                ? Span.current().getSpanContext().getTraceId()
                : null;
        jdbc.update(
                "INSERT INTO audit_log (actor_id, actor_email, action, subject_type, subject_id, detail_json, trace_id)"
                        + " VALUES (?,?,?,?,?,?,?)",
                actorId,
                actorEmail,
                action,
                subjectType,
                subjectId,
                detailJson,
                traceId);
    }
}
