package app.platform.worker.aws;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

/**
 * Execution-role resolution (3.06): manifest ARN wins (cross-account, phase 4), else the
 * per-template SSM role, else the environment default. ARNs never live in template manifests
 * for in-account roles — they're account-specific wiring, which is SSM's job.
 */
@Component
public class ExecRoleResolver {

    private static final Logger log = LoggerFactory.getLogger(ExecRoleResolver.class);

    private final SsmClient ssm;
    private final String env;
    private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();

    public ExecRoleResolver(SsmClient ssm, @Value("${platform.env:${PLATFORM_ENV:dev}}") String env) {
        this.ssm = ssm;
        this.env = env;
    }

    public String resolve(String manifestArn, String templateId) {
        if (manifestArn != null && !manifestArn.isBlank()) {
            return manifestArn;
        }
        return lookup("/platform/" + env + "/exec-roles/" + templateId)
                .or(() -> lookup("/platform/" + env + "/exec-roles/default"))
                .orElseThrow(() -> new IllegalStateException(
                        "No execution role for template '" + templateId + "' — deploy the iam stack"));
    }

    private Optional<String> lookup(String name) {
        return cache.computeIfAbsent(name, key -> {
            try {
                return Optional.of(ssm.getParameter(b -> b.name(key)).parameter().value());
            } catch (ParameterNotFoundException e) {
                log.debug("No SSM exec role at {}", key);
                return Optional.empty();
            }
        });
    }
}
