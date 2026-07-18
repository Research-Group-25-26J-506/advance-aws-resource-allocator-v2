package app.platform.api.cost;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cost visibility API (governance-by-visibility, not quotas). A team owning the platform sees its
 * own spend: month-to-date total + trend, a breakdown by team (CostCenter) and environment, and
 * the cost of an individual resource. Backed by {@link CostService} (Cost Explorer, cached).
 *
 * <p>Any authenticated user can view aggregate spend for their account; per-resource cost sits on
 * the request page they already have access to.
 */
@RestController
@RequestMapping("/api/v1/costs")
public class CostController {

    private final CostService costs;
    private final JdbcTemplate jdbc;

    public CostController(CostService costs, JdbcTemplate jdbc) {
        this.costs = costs;
        this.jdbc = jdbc;
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public CostService.CostSummary summary() {
        return costs.summary();
    }

    /** Spend by team. CostCenter is the tag; enrich with the team's human name from the DB. */
    @GetMapping("/by-team")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> byTeam() {
        Map<String, String> names = teamNamesByCostCenter();
        return costs.breakdownByTag(CostService.TAG_TEAM).stream()
                .map(s -> Map.<String, Object>of(
                        "costCenter", s.key(),
                        "team", names.getOrDefault(s.key(), s.key()),
                        "amount", s.amount()))
                .toList();
    }

    @GetMapping("/by-environment")
    @PreAuthorize("isAuthenticated()")
    public List<CostService.CostSlice> byEnvironment() {
        return costs.breakdownByTag(CostService.TAG_ENV);
    }

    @GetMapping("/resource/{requestId}")
    @PreAuthorize("isAuthenticated()")
    public CostService.ResourceCost resource(@PathVariable String requestId) {
        return costs.forResource(requestId);
    }

    private Map<String, String> teamNamesByCostCenter() {
        try {
            return jdbc.query("SELECT cost_center, name FROM teams", rs -> {
                Map<String, String> m = new java.util.HashMap<>();
                while (rs.next()) {
                    m.put(rs.getString("cost_center"), rs.getString("name"));
                }
                return m;
            });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
