package app.platform.api.approval;

import app.platform.api.request.RequestDtos.RequestDto;
import app.platform.api.request.RequestService;
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

/** Approvals inbox (2.07): PROD requests wait here until an APPROVER signs off. */
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    public record RejectPayload(String reason) {}

    private final RequestService requests;
    private final JdbcTemplate jdbc;

    public ApprovalController(RequestService requests, JdbcTemplate jdbc) {
        this.requests = requests;
        this.jdbc = jdbc;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('APPROVER','PLATFORM_ADMIN')")
    public List<Map<String, Object>> pending() {
        return jdbc.query(
                "SELECT BIN_TO_UUID(id) AS id, template_id, environment, region, resource_name,"
                        + " requester_email, submitted_at FROM requests"
                        + " WHERE status = 'PENDING_APPROVAL' ORDER BY submitted_at ASC LIMIT 100",
                (rs, i) -> Map.of(
                        "id", rs.getString("id"),
                        "templateId", rs.getString("template_id"),
                        "environment", rs.getString("environment"),
                        "region", rs.getString("region"),
                        "resourceName", rs.getString("resource_name"),
                        "requesterEmail", rs.getString("requester_email"),
                        "submittedAt", String.valueOf(rs.getTimestamp("submitted_at"))));
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasAnyRole('APPROVER','PLATFORM_ADMIN')")
    public ResponseEntity<RequestDto> approve(@PathVariable UUID requestId, Authentication auth) {
        return ResponseEntity.accepted().body(RequestDto.from(requests.approve(auth.getName(), requestId)));
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasAnyRole('APPROVER','PLATFORM_ADMIN')")
    public ResponseEntity<RequestDto> reject(
            @PathVariable UUID requestId, @RequestBody RejectPayload payload, Authentication auth) {
        String reason = payload == null || payload.reason() == null ? "No reason given" : payload.reason();
        return ResponseEntity.accepted().body(RequestDto.from(requests.reject(auth.getName(), requestId, reason)));
    }
}
