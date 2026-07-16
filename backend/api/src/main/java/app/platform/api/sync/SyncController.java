package app.platform.api.sync;

import app.platform.api.audit.AuditLogger;
import app.platform.common.UuidV7;
import app.platform.domain.error.NotFoundException;
import app.platform.domain.port.WorkQueue;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin sync API (3.02 subset): trigger, history, detail with events. TEMPLATE_ADMIN only. */
@RestController
@RequestMapping("/api/v1/admin/templates/sync")
public class SyncController {

    public record TriggerPayload(String mode, String branch) {}

    private final JdbcTemplate jdbc;
    private final WorkQueue workQueue;
    private final AuditLogger audit;

    public SyncController(JdbcTemplate jdbc, WorkQueue workQueue, AuditLogger audit) {
        this.jdbc = jdbc;
        this.workQueue = workQueue;
        this.audit = audit;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEMPLATE_ADMIN','PLATFORM_ADMIN')")
    public ResponseEntity<Map<String, Object>> trigger(
            @RequestBody(required = false) TriggerPayload payload,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            Authentication auth) {
        String mode = payload != null && "APPLY".equalsIgnoreCase(payload.mode()) ? "APPLY" : "DRY_RUN";
        String branch = payload != null && payload.branch() != null ? payload.branch() : "develop";
        UUID syncId = UuidV7.generate();
        jdbc.update(
                "INSERT INTO template_syncs (id, actor_id, commit_sha, branch, mode, status, started_at)"
                        + " VALUES (?,?,?,?,?, 'SYNC_PENDING', NOW(6))",
                bytes(syncId), auth.getName(), "HEAD", branch, mode);
        workQueue.enqueueTemplateSync(syncId, idempotencyKey);
        audit.record(auth.getName(), auth.getName(), "SYNC_TRIGGERED", "SYNC", syncId.toString(),
                Map.of("mode", mode, "branch", branch));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/admin/templates/sync/" + syncId))
                .body(Map.of("id", syncId.toString(), "mode", mode, "branch", branch, "status", "SYNC_PENDING"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEMPLATE_ADMIN','PLATFORM_ADMIN')")
    public List<Map<String, Object>> list() {
        return jdbc.query(
                "SELECT BIN_TO_UUID(id) AS id, actor_id, branch, mode, status, started_at, completed_at"
                        + " FROM template_syncs ORDER BY started_at DESC LIMIT 50",
                (rs, i) -> Map.of(
                        "id", rs.getString("id"),
                        "actorId", rs.getString("actor_id"),
                        "branch", rs.getString("branch"),
                        "mode", rs.getString("mode"),
                        "status", rs.getString("status"),
                        "startedAt", String.valueOf(rs.getTimestamp("started_at")),
                        "completedAt", String.valueOf(rs.getTimestamp("completed_at"))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEMPLATE_ADMIN','PLATFORM_ADMIN')")
    public Map<String, Object> get(@PathVariable UUID id) {
        var rows = jdbc.queryForList(
                "SELECT BIN_TO_UUID(id) AS id, actor_id, branch, mode, status, started_at, completed_at,"
                        + " summary_json, error_json FROM template_syncs WHERE id = ?",
                bytes(id));
        if (rows.isEmpty()) {
            throw new NotFoundException("Sync", id);
        }
        var events = jdbc.queryForList(
                "SELECT from_status, to_status, detail, occurred_at FROM sync_events"
                        + " WHERE sync_id = ? ORDER BY occurred_at ASC",
                bytes(id));
        Map<String, Object> result = new java.util.HashMap<>(rows.get(0));
        result.put("events", events);
        return result;
    }

    private static byte[] bytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }
}
