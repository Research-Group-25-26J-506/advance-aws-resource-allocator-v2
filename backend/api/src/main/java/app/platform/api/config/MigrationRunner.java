package app.platform.api.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Dedicated migration entrypoint (Phase A). Run the API image with the `migrate` profile as a
 * one-off pre-deploy task: it applies Flyway migrations and exits 0 (or non-zero on failure, so
 * the deploy halts BEFORE rolling services). A bad migration fails the job, not the running API.
 * In normal profiles Flyway still runs at boot as an idempotent safety net (it locks + skips
 * already-applied versions).
 */
@Component
@Profile("migrate")
@Order(1)
public class MigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final Flyway flyway;
    private final ApplicationContext context;

    public MigrationRunner(Flyway flyway, ApplicationContext context) {
        this.flyway = flyway;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        int result = 0;
        try {
            var applied = flyway.migrate();
            log.info("Migrations applied: {} (schema now at {})", applied.migrationsExecuted, applied.targetSchemaVersion);
        } catch (Exception e) {
            log.error("Migration failed — deploy must halt", e);
            result = 1;
        }
        final int code = result;
        System.exit(org.springframework.boot.SpringApplication.exit(context, () -> code));
    }
}
