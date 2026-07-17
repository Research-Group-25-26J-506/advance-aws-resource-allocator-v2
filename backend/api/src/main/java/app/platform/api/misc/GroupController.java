package app.platform.api.misc;

import app.platform.api.audit.AuditLogger;
import app.platform.common.UuidV7;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Resource groups (first-class): create the group, then attach resources by provisioning into it. */
@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    public record CreatePayload(String name, String description) {}

    private final JdbcTemplate jdbc;
    private final AuditLogger audit;

    public GroupController(JdbcTemplate jdbc, AuditLogger audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> list() {
        return jdbc.query(
                "SELECT name, description, created_by, created_at FROM resource_groups ORDER BY name",
                (rs, i) -> Map.of(
                        "name", rs.getString("name"),
                        "description", rs.getString("description") == null ? "" : rs.getString("description"),
                        "createdBy", rs.getString("created_by"),
                        "createdAt", String.valueOf(rs.getTimestamp("created_at"))));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','PLATFORM_ADMIN')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreatePayload payload, Authentication auth) {
        String name = payload.name() == null ? "" : payload.name().trim().toLowerCase();
        if (!name.matches("^[a-z][a-z0-9-]{2,62}$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("detail", "Group name: lowercase letters, digits, hyphens; 3-63 chars"));
        }
        UUID id = UuidV7.generate();
        ByteBuffer bytes = ByteBuffer.allocate(16);
        bytes.putLong(id.getMostSignificantBits());
        bytes.putLong(id.getLeastSignificantBits());
        jdbc.update(
                "INSERT IGNORE INTO resource_groups (id, name, description, created_by) VALUES (?,?,?,?)",
                bytes.array(), name, payload.description(), auth.getName());
        audit.record(auth.getName(), auth.getName(), "GROUP_CREATED", "GROUP", name, Map.of());
        return ResponseEntity.ok(Map.of("name", name));
    }
}
