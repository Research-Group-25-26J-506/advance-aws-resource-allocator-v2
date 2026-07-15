package app.platform.api.config;

import app.platform.domain.service.TagPolicyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Domain services are plain Java — wired as beans here, never annotated in :domain. */
@Configuration
public class DomainConfig {

    @Bean
    public TagPolicyService tagPolicyService() {
        return new TagPolicyService();
    }
}
