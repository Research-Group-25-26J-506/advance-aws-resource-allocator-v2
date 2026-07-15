package app.platform.api.misc;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public runtime configuration for the SPA (none of this is secret — client id and issuer are
 * embedded in every token anyway). Lets one web-ui image serve every environment.
 */
@RestController
public class ConfigController {

    @Value("${platform.auth.issuer:${PLATFORM_COGNITO_ISSUER:}}")
    private String issuer;

    @Value("${platform.auth.web-client-id:${PLATFORM_WEB_CLIENT_ID:}}")
    private String webClientId;

    @Value("${platform.auth.domain:${PLATFORM_COGNITO_DOMAIN:}}")
    private String cognitoDomain;

    @GetMapping("/api/v1/config")
    public Map<String, String> config() {
        return Map.of(
                "authMode", issuer.isBlank() ? "dev-bypass" : "cognito",
                "issuer", issuer,
                "clientId", webClientId,
                "cognitoDomain", cognitoDomain);
    }
}
