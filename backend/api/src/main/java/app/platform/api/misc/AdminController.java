package app.platform.api.misc;

import app.platform.api.audit.AuditLogger;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Platform admin operations. {@code reset} wipes the platform's OPERATIONAL records — requests,
 * request events, resource outputs, resource groups, and builds — for a clean slate. It does NOT
 * touch the template catalog, teams, or the audit trail, and it does NOT delete any AWS resources:
 * delete the resources' CloudFormation stacks first (or via the group delete), then reset so the
 * request history matches reality. Guarded to PLATFORM_ADMIN and requires ?confirm=true.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final JdbcTemplate jdbc;
    private final AuditLogger audit;

    public AdminController(JdbcTemplate jdbc, AuditLogger audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @PostMapping("/reset")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public Map<String, Object> reset(@RequestParam(defaultValue = "false") boolean confirm, Authentication auth) {
        if (!confirm) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This wipes all requests, resource groups and builds (not the catalog/teams/audit)."
                            + " Delete the CloudFormation stacks first, then call with ?confirm=true.");
        }
        // Children first (FK order), then parents. Catalog / teams / audit are preserved.
        int events = safeDelete("request_events");
        int outputs = safeDelete("resource_outputs");
        int requests = safeDelete("requests");
        int builds = safeDelete("builds");
        int groups = safeDelete("resource_groups");
        var counts = Map.<String, Object>of("requests", requests, "requestEvents", events,
                "resourceOutputs", outputs, "groups", groups, "builds", builds);
        audit.record(auth.getName(), auth.getName(), "PLATFORM_RESET", "PLATFORM", "all", counts);
        log.warn("PLATFORM RESET by {} — {}", auth.getName(), counts);
        return counts;
    }

    /** Tolerate tables that may not exist in a given schema version. */
    private int safeDelete(String table) {
        try {
            return jdbc.update("DELETE FROM " + table);
        } catch (Exception e) {
            log.warn("reset: skipped {} ({})", table, e.getMessage());
            return 0;
        }
    }
}
