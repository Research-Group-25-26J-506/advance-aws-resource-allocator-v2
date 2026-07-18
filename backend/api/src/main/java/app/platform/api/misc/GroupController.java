package app.platform.api.misc;

import app.platform.api.audit.AuditLogger;
import app.platform.api.request.RequestService;
import app.platform.common.UuidV7;
import app.platform.domain.model.Environment;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Resource groups (first-class): create the group, then attach resources by provisioning into it. */
@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    public record CreatePayload(String name, String description) {}

    private final JdbcTemplate jdbc;
    private final AuditLogger audit;
    private final RequestService requests;
    private final ObjectMapper mapper;

    public GroupController(JdbcTemplate jdbc, AuditLogger audit, RequestService requests, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.requests = requests;
        this.mapper = mapper;
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

    /** Cascade delete: delete every live resource in the group (each becomes a delete request). */
    @PostMapping("/{name}/delete")
    @PreAuthorize("hasAnyRole('USER','PLATFORM_ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteAll(@PathVariable String name, Authentication auth) {
        List<byte[]> ids = jdbc.queryForList(
                "SELECT id FROM requests WHERE resource_name = ?"
                        + " AND status IN ('CREATE_COMPLETE','UPDATE_COMPLETE')",
                byte[].class, name);
        int deleted = 0;
        for (byte[] raw : ids) {
            try {
                requests.delete(auth.getName(), toUuid(raw));
                deleted++;
            } catch (Exception ignored) {
                // skip anything not in a deletable state
            }
        }
        audit.record(auth.getName(), auth.getName(), "GROUP_DELETED", "GROUP", name, Map.of("count", deleted));
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    /**
     * Restore: re-provision each previously deleted member from its saved configuration. Restores
     * to the template's latest published version with the original params, environment, and region.
     */
    @PostMapping("/{name}/restore")
    @PreAuthorize("hasAnyRole('USER','PLATFORM_ADMIN')")
    public ResponseEntity<Map<String, Object>> restore(@PathVariable String name, Authentication auth) {
        // latest deleted request per (template, env) — the thing to bring back
        var members = jdbc.queryForList(
                "SELECT r.template_id, r.environment, r.region, r.form_data_json, r.custom_tags_json FROM requests r"
                        + " JOIN (SELECT template_id, environment, MAX(submitted_at) AS latest FROM requests"
                        + "       WHERE resource_name = ? AND status = 'DELETE_COMPLETE'"
                        + "       GROUP BY template_id, environment) m"
                        + " ON r.template_id = m.template_id AND r.environment = m.environment"
                        + " AND r.submitted_at = m.latest WHERE r.resource_name = ?",
                name, name);
        int restored = 0;
        for (var member : members) {
            try {
                Map<String, Object> formData =
                        mapper.readValue((String) member.get("form_data_json"), new TypeReference<>() {});
                String tagsJson = (String) member.get("custom_tags_json");
                Map<String, String> customTags = tagsJson == null || tagsJson.isBlank()
                        ? Map.of()
                        : mapper.readValue(tagsJson, new TypeReference<>() {});
                requests.submit(
                        auth.getName(),
                        auth.getName(),
                        (String) member.get("template_id"),
                        Environment.valueOf((String) member.get("environment")),
                        (String) member.get("region"),
                        name,
                        formData,
                        customTags,
                        UuidV7.generate().toString());
                restored++;
            } catch (Exception ignored) {
                // a live member of the same name/env would collide — skip it
            }
        }
        audit.record(auth.getName(), auth.getName(), "GROUP_RESTORED", "GROUP", name, Map.of("count", restored));
        return ResponseEntity.ok(Map.of("restored", restored));
    }

    private static UUID toUuid(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }
}
