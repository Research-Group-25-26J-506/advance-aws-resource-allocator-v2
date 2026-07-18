package app.platform.worker.reconcile;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.CloudFormationException;

/**
 * Backstop for stuck requests (Phase A reliability). A request left in *_IN_PROGRESS beyond a
 * grace window — because a status event was missed or a worker died mid-flight — is reconciled
 * against the ACTUAL CloudFormation stack state. This is the safety net the message-loss fix and
 * the EventBridge listener sit above; it guarantees no request stays wedged.
 */
@Component
public class ReconciliationSweep {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationSweep.class);

    // CFN status -> platform request status (top-level stack states we can act on)
    private static final Map<String, String> CFN_TO_STATUS = Map.ofEntries(
            Map.entry("CREATE_COMPLETE", "CREATE_COMPLETE"),
            Map.entry("CREATE_FAILED", "CREATE_FAILED"),
            Map.entry("ROLLBACK_COMPLETE", "ROLLBACK_COMPLETE"),
            Map.entry("ROLLBACK_FAILED", "ROLLBACK_FAILED"),
            Map.entry("UPDATE_COMPLETE", "UPDATE_COMPLETE"),
            Map.entry("UPDATE_FAILED", "UPDATE_FAILED"),
            Map.entry("UPDATE_ROLLBACK_COMPLETE", "UPDATE_ROLLBACK_COMPLETE"),
            Map.entry("DELETE_COMPLETE", "DELETE_COMPLETE"),
            Map.entry("DELETE_FAILED", "DELETE_FAILED"));

    private final JdbcTemplate jdbc;
    private final CloudFormationClient cfn;

    public ReconciliationSweep(JdbcTemplate jdbc, CloudFormationClient cfn) {
        this.jdbc = jdbc;
        this.cfn = cfn;
    }

    /** Every 5 minutes; only touches requests stuck > 15 minutes so it never races live work. */
    @Scheduled(fixedDelayString = "${platform.reconcile.interval-ms:300000}", initialDelay = 120000)
    public void sweep() {
        List<Map<String, Object>> stuck;
        try {
            stuck = jdbc.queryForList(
                    "SELECT BIN_TO_UUID(id) AS id, stack_id, status FROM requests"
                            + " WHERE status LIKE '%\\_IN\\_PROGRESS'"
                            + " AND updated_at < (NOW(6) - INTERVAL 15 MINUTE)"
                            + " LIMIT 50");
        } catch (Exception e) {
            log.warn("Reconciliation sweep query failed", e);
            return;
        }
        if (stuck.isEmpty()) {
            return;
        }
        log.info("Reconciliation sweep: {} request(s) stuck > 15m", stuck.size());
        for (Map<String, Object> row : stuck) {
            reconcile((String) row.get("id"), (String) row.get("stack_id"), (String) row.get("status"));
        }
    }

    private void reconcile(String requestId, String stackId, String current) {
        if (stackId == null || stackId.isBlank()) {
            // Never got a stack — a lost QUEUED/CREATE message. Nothing to describe; a delete
            // that never created can complete, otherwise leave for an operator.
            if ("DELETE_IN_PROGRESS".equals(current)) {
                apply(requestId, current, "DELETE_COMPLETE", "No stack existed");
            }
            return;
        }
        try {
            var stacks = cfn.describeStacks(b -> b.stackName(stackId)).stacks();
            if (stacks.isEmpty()) {
                return;
            }
            String cfnStatus = stacks.get(0).stackStatusAsString();
            String target = CFN_TO_STATUS.get(cfnStatus);
            if (target != null && !target.equals(current)) {
                apply(requestId, current, target, "Reconciled from CFN " + cfnStatus);
            }
        } catch (CloudFormationException e) {
            // Stack gone: a completed delete whose event we missed.
            if (e.getMessage() != null && e.getMessage().contains("does not exist")
                    && current.startsWith("DELETE")) {
                apply(requestId, current, "DELETE_COMPLETE", "Stack no longer exists");
            }
        } catch (Exception e) {
            log.warn("Could not reconcile {}", requestId, e);
        }
    }

    private void apply(String requestId, String from, String to, String reason) {
        jdbc.update("UPDATE requests SET status = ? WHERE id = UUID_TO_BIN(?)", to, requestId);
        jdbc.update(
                "INSERT INTO request_events (request_id, from_status, to_status, reason, source, occurred_at)"
                        + " VALUES (UUID_TO_BIN(?), ?, ?, ?, 'PLATFORM', NOW(6))",
                requestId, from, to, "Reconciliation sweep: " + reason);
        log.info("Reconciled {} {} -> {} ({})", requestId, from, to, reason);
    }
}
