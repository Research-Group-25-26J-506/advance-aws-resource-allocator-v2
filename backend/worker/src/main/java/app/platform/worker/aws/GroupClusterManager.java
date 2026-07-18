package app.platform.worker.aws;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Tag;

/**
 * Per-group compute isolation: every resource group gets its OWN ECS cluster so deployed services
 * never run in the platform control-plane cluster (which holds api/worker/grafana/loki/tempo).
 *
 * <p>Idempotent by design — CreateCluster on an already-ACTIVE cluster is a no-op — so it's safe to
 * call on every service provision: the first service in a group creates the cluster, the rest reuse
 * it. Fargate services set LaunchType FARGATE, so a plain cluster (no capacity providers) suffices.
 * Cluster name: {@code platform-grp-<group>-<env>} (group = the request's resourceName).
 */
@Component
public class GroupClusterManager {

    private static final Logger log = LoggerFactory.getLogger(GroupClusterManager.class);

    private final EcsClient ecs;

    public GroupClusterManager(EcsClient ecs) {
        this.ecs = ecs;
    }

    /** Ensure the group's cluster exists; returns its name. Never throws — a bad ensure surfaces
     * as a clear stack-create error and the retry re-runs this. */
    public String ensureCluster(String group, String env) {
        String name = clusterName(group, env);
        try {
            boolean active = ecs.describeClusters(b -> b.clusters(name)).clusters().stream()
                    .anyMatch(c -> "ACTIVE".equals(c.status()));
            if (active) {
                return name;
            }
            ecs.createCluster(b -> b.clusterName(name)
                    .tags(
                            Tag.builder().key("ManagedBy").value("Platform").build(),
                            Tag.builder().key("ResourceGroup").value(group).build(),
                            Tag.builder().key("Environment").value(env).build()));
            log.info("Created per-group ECS cluster {}", name);
        } catch (Exception e) {
            log.warn("Could not ensure ECS cluster {} (service create will surface the error): {}",
                    name, e.getMessage());
        }
        return name;
    }

    public static String clusterName(String group, String env) {
        return "platform-grp-" + group + "-" + env.toLowerCase(Locale.ROOT);
    }
}
