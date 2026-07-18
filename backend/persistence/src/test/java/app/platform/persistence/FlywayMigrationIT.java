package app.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test (Phase A): every Flyway migration must apply cleanly against a real MySQL 8,
 * and the core tables must be usable. Catches migration bugs — utf8mb4 collation, JSON CHECK
 * constraints, FK ordering — that unit tests can't see. Requires Docker; skipped when absent.
 */
@Testcontainers
class FlywayMigrationIT {

    private static MySQLContainer<?> mysql;
    private static boolean dockerAvailable = true;

    @BeforeAll
    static void up() {
        try {
            mysql = new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("platform")
                    .withUsername("platform")
                    .withPassword("platform");
            mysql.start();
        } catch (Throwable t) {
            dockerAvailable = false; // no Docker in this environment — the test self-skips
        }
    }

    @AfterAll
    static void down() {
        if (mysql != null && mysql.isRunning()) {
            mysql.stop();
        }
    }

    @Test
    void allMigrationsApplyAndCoreTablesWork() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dockerAvailable, "Docker not available — skipping");

        Flyway flyway = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load();
        var result = flyway.migrate();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(8); // V001..V008

        DataSource ds = flyway.getConfiguration().getDataSource();
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement()) {
            // insert a team + request (exercises utf8mb4 + JSON column + FK)
            s.execute(
                    "INSERT INTO teams (id, name, cost_center) VALUES (UNHEX(REPLACE(UUID(),'-','')), 'qa', 'CC-1')");
            byte[] teamId;
            try (ResultSet rs = s.executeQuery("SELECT id FROM teams LIMIT 1")) {
                rs.next();
                teamId = rs.getBytes(1);
            }
            s.execute(
                    "INSERT INTO templates (id, display_name, description, category, maturity)"
                            + " VALUES ('s3-bucket','S3','desc','Storage','stable')");
            try (var ps = c.prepareStatement(
                    "INSERT INTO template_versions (id, template_id, version, commit_sha, s3_key_body,"
                            + " s3_key_schema, s3_key_manifest, status) VALUES (UNHEX(REPLACE(UUID(),'-','')),"
                            + " 's3-bucket','1.0.0','abc','b','s','m','PUBLISHED')")) {
                ps.execute();
            }
            byte[] versionId;
            try (ResultSet rs = s.executeQuery("SELECT id FROM template_versions LIMIT 1")) {
                rs.next();
                versionId = rs.getBytes(1);
            }
            try (var ps = c.prepareStatement(
                    "INSERT INTO requests (id, template_version_id, template_id, requester_id, requester_email,"
                            + " team_id, environment, region, resource_name, status, form_data_json,"
                            + " idempotency_key, submitted_at) VALUES (UNHEX(REPLACE(UUID(),'-','')), ?, 's3-bucket',"
                            + " 'u','u@e', ?, 'DEV','us-east-1','r','QUEUED', '{\"k\":\"v\"}', 'key1', NOW(6))")) {
                ps.setBytes(1, versionId);
                ps.setBytes(2, teamId);
                ps.execute();
            }
            try (ResultSet rs = s.executeQuery(
                    "SELECT environment, JSON_EXTRACT(form_data_json,'$.k') FROM requests")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("DEV");
                assertThat(rs.getString(2).trim().replace("\"", "")).isEqualTo("v");
            }
        }
    }
}
