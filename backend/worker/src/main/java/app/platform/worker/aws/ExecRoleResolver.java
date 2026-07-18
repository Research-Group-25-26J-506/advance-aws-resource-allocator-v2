package app.platform.worker.aws;

import app.platform.domain.model.Environment;
import java.util.Locale;
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
 *
 * <p>Environment-as-attribute (single-platform model): the role is resolved under the REQUEST's
 * environment namespace — {@code /platform/{requestEnv}/exec-roles/...} — not the control-plane's
 * own env. This is what lets one platform deployment provision-and-promote the same resource up
 * through dev → qa → stg → prod, assuming the target env's scoped role each time. The injected
 * {@code defaultEnv} is only a fallback when a caller has no request environment.
 */
@Component
public class ExecRoleResolver {

    private static final Logger log = LoggerFactory.getLogger(ExecRoleResolver.class);

    private final SsmClient ssm;
    private final String defaultEnv;
    private final Map<String, Optional<String>> cache = new ConcurrentHashMap<>();

    public ExecRoleResolver(SsmClient ssm, @Value("${platform.env:${PLATFORM_ENV:dev}}") String defaultEnv) {
        this.ssm = ssm;
        this.defaultEnv = defaultEnv;
    }

    public String resolve(String manifestArn, String templateId, Environment env) {
        if (manifestArn != null && !manifestArn.isBlank()) {
            return manifestArn;
        }
        String ns = env != null ? env.name().toLowerCase(Locale.ROOT) : defaultEnv;
        return lookup("/platform/" + ns + "/exec-roles/" + templateId)
                .or(() -> lookup("/platform/" + ns + "/exec-roles/default"))
                .orElseThrow(() -> new IllegalStateException("No execution role for template '" + templateId
                        + "' in env '" + ns + "' — deploy the iam stack for that environment"));
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
