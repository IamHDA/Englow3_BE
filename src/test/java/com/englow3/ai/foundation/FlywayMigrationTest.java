package com.englow3.ai.foundation;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class FlywayMigrationTest {

    private static final Map<String, Integer> PRODUCTION_CHECKSUMS = Map.ofEntries(
            entry("create set updated at function", -2087206501), entry("create users table", -2113663597),
            entry("create learner profiles table", 623186956), entry("create learning purposes table", 660755909),
            entry("create user learning purposes table", -1051684683),
            entry("create user target skills table", 1922562307), entry("create exams table", 706526194),
            entry("create exam sections table", -1750452832), entry("create section parts table", -1904413149),
            entry("create question sets table", 160178663), entry("create question set options table", -951765632),
            entry("create questions table", -1667187594), entry("create question options table", -413418663),
            entry("create question matching answers table", -2048791941),
            entry("create question accepted answers table", -1382312851),
            entry("create grading criteria table", -1478831944), entry("create score conversions table", 1230693728),
            entry("create exam attempts table", -1950497065), entry("create attempt section results table", 1922207971),
            entry("create attempt answers table", 972162187), entry("create attempt answer options table", 188066640),
            entry("create attempt answer criterion scores table", 1682411062),
            entry("create ai jobs table", 1154737702), entry("create sync auth user trigger", -133326690),
            entry("add placement attempt to learner profiles", -1925075099), entry("create content tables", -610581241),
            entry("add shadowing and assessment content", 1024711226), entry("harden ai job platform", 1577060752),
            entry("create ai tutor", -451425355), entry("create ai placement", -1848102994),
            entry("create ai learning paths", -977591622), entry("create ai speaking", -2001375234),
            entry("create ai content governance", -2083092448), entry("harden ai operations", -36488122),
            entry("route ai models through ai service", 106985282), entry("create ai writing assessment", 2017853865),
            entry("harden ai content publication", -510248765), entry("create personalized exam blueprints", 222188760),
            entry("harden adaptive learning events", -216892673), entry("harden semantic tutor", 819848983),
            entry("harden speaking coach", 1449689556), entry("create ai job event outbox", 872937914),
            entry("create ai evaluation gates", 1314450469), entry("create adaptive placement", -1120715918),
            entry("create ai embedding index", -1711301871), entry("sync role to auth app metadata", 2053235061),
            entry("seed learning purposes", 1133164813));

    private void migrate(String url, String username, String password) {
        createSupabaseAuthFixture(url, username, password);

        Flyway existingProductionSchema = configureFlyway(url, username, password)
                .target(MigrationVersion.fromVersion("46")).load();
        assertThat(existingProductionSchema.migrate().success).isTrue();
        assertThat(existingProductionSchema.info().current().getVersion())
                .isEqualTo(MigrationVersion.fromVersion("46"));
        Map<String, Integer> actualChecksums = Arrays.stream(existingProductionSchema.info().applied())
                .filter(info -> info.getChecksum() != null)
                .collect(Collectors.toMap(info -> info.getDescription(), info -> info.getChecksum()));
        assertThat(actualChecksums).isEqualTo(PRODUCTION_CHECKSUMS);

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

    @Nested
    class Success {

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
            DockerImageName image = DockerImageName.parse("pgvector/pgvector:pg17")
                    .asCompatibleSubstituteFor("postgres");
            try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(image).withDatabaseName("englow3")
                    .withUsername("englow3").withPassword("englow3")) {
                postgres.start();
                migrate(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            }
        }

    }

}
