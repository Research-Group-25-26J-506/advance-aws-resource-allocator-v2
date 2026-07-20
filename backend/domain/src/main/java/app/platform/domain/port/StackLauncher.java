package app.platform.domain.port;

import app.platform.domain.model.Environment;
import app.platform.domain.model.Tag;
import java.util.List;
import java.util.Map;

/**
 * CloudFormation execution port. The real adapter assumes the per-template execution role via
 * STS before calling CFN; the `local` profile binds a stub driver that fakes stack lifecycles.
 */
public interface StackLauncher {

    record StackLaunch(
            String stackName,
            String templateBody, // null when templateUrl set (51,200-byte inline limit)
            String templateUrl,
            Map<String, String> parameters,
            List<Tag> tags,
            List<String> capabilities,
            String executionRoleArn,
            Environment environment,
            String region,
            String clientRequestToken) {}

    /** @return the created StackId */
    String createStack(StackLaunch launch);

    /**
     * In-place update of an existing stack ({@code launch.stackName()} carries the existing stack
     * id/name). CloudFormation rolls the change (e.g. a new task-definition image) with no downtime.
     * Throws if there is nothing to change — the caller treats "no updates" as an immediate success.
     */
    void updateStack(StackLaunch launch);

    void deleteStack(String stackId, String executionRoleArn, Environment environment, String region);
}
