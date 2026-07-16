package app.platform.api.misc;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Audit log (2.11) + runbook registry (3.14) read endpoints. */
@RestController
@RequestMapping("/api/v1")
public class AuditController {

    private final JdbcTemplate jdbc;

    public AuditController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','APPROVER')")
    public List<Map<String, Object>> audit() {
        return jdbc.query(
                "SELECT actor_id, action, subject_type, subject_id, detail_json, trace_id, occurred_at"
                        + " FROM audit_log ORDER BY occurred_at DESC LIMIT 200",
                (rs, i) -> Map.of(
                        "actorId", rs.getString("actor_id"),
                        "action", rs.getString("action"),
                        "subjectType", rs.getString("subject_type"),
                        "subjectId", rs.getString("subject_id"),
                        "detail", rs.getString("detail_json") == null ? "" : rs.getString("detail_json"),
                        "traceId", rs.getString("trace_id") == null ? "" : rs.getString("trace_id"),
                        "occurredAt", String.valueOf(rs.getTimestamp("occurred_at"))));
    }

    @GetMapping("/runbooks")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> runbooks() {
        return jdbc.query(
                "SELECT id, path_in_repo, title, severity FROM runbooks ORDER BY id",
                (rs, i) -> Map.of(
                        "id", rs.getString("id"),
                        "path", rs.getString("path_in_repo"),
                        "title", rs.getString("title"),
                        "severity", rs.getString("severity")));
    }
}
