package com.englow3.ai.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class FlywayMigrationTest {

    @Test
    void appliesEveryMigrationToAnEmptyPostgresDatabase() {
        String externalUrl = System.getProperty("it.postgres.url", "");
        if (!externalUrl.isBlank()) {
            migrate(externalUrl, System.getProperty("it.postgres.username", "postgres"),
                    System.getProperty("it.postgres.password", "postgres"));
            return;
        }
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is unavailable and no external integration-test database was configured");
        DockerImageName image = DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres");
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(image).withDatabaseName("englow3")
                .withUsername("englow3").withPassword("englow3")) {
            postgres.start();
            migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        }
    }

    private void migrate(String url, String username, String password) {
        createSupabaseAuthFixture(url, username, password);

        Flyway existingProductionSchema = configureFlyway(url, username, password)
                .target(MigrationVersion.fromVersion("29")).load();
        assertThat(existingProductionSchema.migrate().success).isTrue();
        assertThat(existingProductionSchema.info().current().getVersion())
                .isEqualTo(MigrationVersion.fromVersion("29"));

        Flyway flyway = configureFlyway(url, username, password).load();
        assertThat(flyway.migrate().success).isTrue();
        flyway.validate();
        assertThat(flyway.info().pending()).isEmpty();
    }

    private FluentConfiguration configureFlyway(String url, String username, String password) {
        return Flyway.configure().dataSource(url, username, password).schemas("englow3").defaultSchema("englow3")
                .locations("classpath:db/migration");
    }

    private void createSupabaseAuthFixture(String url, String username, String password) {
        try (var connection = DriverManager.getConnection(url, username, password);
                var statement = connection.createStatement()) {
            statement.execute("create schema if not exists auth");
            statement.execute("""
                    create table if not exists auth.users (
                        id uuid primary key,
                        email varchar(320) not null,
                        raw_user_meta_data jsonb not null default '{}'::jsonb
                    )
                    """);
        } catch (SQLException ex) {
            throw new IllegalStateException("Could not prepare the Supabase auth fixture", ex);
        }
    }
}
