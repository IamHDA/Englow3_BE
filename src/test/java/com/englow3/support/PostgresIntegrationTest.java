package com.englow3.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * A real PostgreSQL, with the real migrations run against it.
 * <p>
 * Everything else in this suite mocks the repository layer, which is the right trade for testing a decision but leaves
 * the read models - hand-written SQL behind {@code JdbcClient} - with nothing checking them at all. A join that returns
 * the wrong rows is invisible to a mock, and would surface on the screen it feeds rather than in a test.
 * <p>
 * One container for the whole suite: starting Postgres costs seconds, and paying that per test class would make the
 * integration tests slow enough that someone turns them off. Each test is responsible for the rows it creates.
 */
@SpringBootTest
@Tag("integration")
public abstract class PostgresIntegrationTest {

    /**
     * Started once and deliberately never stopped - Ryuk removes it when the JVM exits. {@code static} is what makes it
     * shared; a non-static container would be a fresh database per test class.
     */
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("englow3").withUsername("englow3").withPassword("englow3")
            // The migrations put triggers on auth.users, which Supabase owns in production. Creating the slice they
            // touch before Flyway runs is what lets the real migration files run here unchanged - the alternative is
            // a test-only copy of the schema, which would stop being the schema the first time one changed.
            .withCopyFileToContainer(MountableFile.forClasspathResource("db/supabase-auth-stub.sql"),
                    "/docker-entrypoint-initdb.d/00-auth.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // Flyway builds the schema; Hibernate must not also try. `validate` would be the stricter choice but fails on
        // details Flyway is allowed to differ on, and `none` keeps one owner of the schema.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.clean-disabled", () -> "false");

        // The worker would otherwise start draining the queue underneath these tests.
        registry.add("app.ai.enabled", () -> "false");

        // The only two settings with no default. They are deliberately required in production - an application that
        // silently starts with no token issuer would accept nothing and say nothing about why - so the context needs
        // them here too. Nothing in these tests verifies a token, so the values only have to parse.
        registry.add("SUPABASE_ISSUER_URI", () -> "https://project.supabase.co/auth/v1");
        registry.add("SUPABASE_JWKS_URI", () -> "https://project.supabase.co/auth/v1/.well-known/jwks.json");

        // The S3 client refuses to be built with a blank key, and it is built eagerly at startup. These are never
        // used: nothing here uploads, downloads or signs. A MinIO container would be honest about that but would pay
        // for a second container to hold files no test reads.
        registry.add("app.storage.s3.access-key", () -> "integration-test");
        registry.add("app.storage.s3.secret-key", () -> "integration-test");
    }
}
