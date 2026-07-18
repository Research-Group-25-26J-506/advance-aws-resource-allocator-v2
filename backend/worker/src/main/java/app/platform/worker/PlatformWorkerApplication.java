package app.platform.worker;

import app.platform.domain.port.BodyTemplater;
import app.platform.domain.service.TagPolicyService;
import app.platform.domain.service.TemplateRenderer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "app.platform")
@org.springframework.scheduling.annotation.EnableScheduling
// considerNestedRepositories: the repo interfaces are nested inside SpringDataRepos
@EnableJpaRepositories(basePackages = "app.platform.persistence", considerNestedRepositories = true)
@EntityScan(basePackages = "app.platform.persistence.entity")
public class PlatformWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformWorkerApplication.class, args);
    }

    @Bean
    public TagPolicyService tagPolicyService() {
        return new TagPolicyService();
    }

    @Bean
    public TemplateRenderer templateRenderer(BodyTemplater bodyTemplater) {
        return new TemplateRenderer(bodyTemplater);
    }
}
