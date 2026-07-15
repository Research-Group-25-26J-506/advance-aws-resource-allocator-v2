package app.platform.api.misc;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MeController {

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> me(Authentication auth) {
        return Map.of(
                "id", auth.getName(),
                "email", auth.getName(),
                "roles",
                        auth.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .map(a -> a.replace("ROLE_", ""))
                                .toList());
    }

    /** Region allowlist per environment — the wizard's region Select reads this (2.04). */
    @GetMapping("/regions")
    @PreAuthorize("isAuthenticated()")
    public List<String> regions(@RequestParam(defaultValue = "DEV") String env) {
        return switch (env.toUpperCase()) {
            case "PROD" -> List.of("us-east-1", "eu-west-1");
            default -> List.of("us-east-1", "us-west-2", "eu-west-1", "ap-southeast-1");
        };
    }

    /** Environment health snapshot for the dashboard (2.02). Static until 4.x wires real checks. */
    @GetMapping("/environments/health")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> environmentsHealth() {
        return List.of(
                Map.of("environment", "DEV", "status", "HEALTHY", "lastIncidentAt", ""),
                Map.of("environment", "STG", "status", "HEALTHY", "lastIncidentAt", ""),
                Map.of("environment", "PROD", "status", "HEALTHY", "lastIncidentAt", ""));
    }

    /** KPI contract pinned down (2.02 enhancement): API computes deltas, UI never does. */
    @GetMapping("/dashboards/kpis")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> kpis() {
        return Map.of(
                "active_requests", Map.of("value", 0, "delta_pct", 0, "direction", "flat"),
                "successful_30d", Map.of("value", 0, "delta_pct", 0, "direction", "flat"),
                "failed_30d", Map.of("value", 0, "delta_pct", 0, "direction", "flat"),
                "avg_completion_seconds", Map.of("value", 0, "delta_pct", 0, "direction", "flat"));
    }
}
