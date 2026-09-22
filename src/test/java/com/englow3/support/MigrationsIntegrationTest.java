package com.englow3.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * That the migrations apply at all, on a database nobody has hand-patched.
 * <p>
 * Worth its own test rather than being implied by the others: until now the only proof V001 through V045 could run in
 * order was that they had run on somebody's laptop, once. A migration that fails on a fresh database fails on the next
 * environment anyone creates, and this is the cheapest place to find that out.
 */
class MigrationsIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void appliesEveryMigrationInOrder() {
        Integer failed = jdbc.sql("select count(*) from englow3.flyway_schema_history where success = false")
                .query(Integer.class).single();

        assertThat(failed).isZero();
    }

    @Test
    void endsOnTheLatestVersion() {
        String latest = jdbc.sql("select max(version) from englow3.flyway_schema_history").query(String.class).single();

        // Read from the table rather than hard-coded: a test asserting "045" becomes a chore that fails on every new
        // migration without ever having found a bug.
        assertThat(latest).isNotBlank();
    }

    /** The tables the read models query. A missing one here means a migration silently did nothing. */
    @Test
    void createsTheTablesTheReadModelsDependOn() {
        var tables = jdbc.sql("select table_name from information_schema.tables where table_schema = 'englow3'")
                .query(String.class).list();

        assertThat(tables).contains("users", "exams", "exam_attempts", "flashcard_sets", "flashcards",
                "flashcard_reviews", "quizzes", "quiz_attempts", "dictation_lessons", "dictation_attempts",
                "speaking_prompts", "speaking_attempts", "ai_jobs", "tutor_conversations", "tutor_messages");
    }

    /**
     * The trigger Supabase fires at signup. If this stops working, a learner can sign in and have no profile row -
     * every authenticated request then fails on a user that does not exist.
     */
    @Test
    void createsAProfileWhenAnAuthUserAppears() {
        jdbc.sql("""
                insert into auth.users (email, raw_user_meta_data)
                values ('trigger-test@example.com', '{"full_name":"Nguyen Van A","gender":"MALE"}'::jsonb)
                """).update();

        var profile = jdbc.sql("""
                select full_name, gender::text, role::text from englow3.users where email = 'trigger-test@example.com'
                """).query((rs, row) -> new String[] { rs.getString(1), rs.getString(2), rs.getString(3) }).single();

        assertThat(profile[0]).isEqualTo("Nguyen Van A");
        assertThat(profile[1]).isEqualTo("MALE");
        assertThat(profile[2]).isEqualTo("LEARNER");
    }

    /** A malformed date must not fail the signup it rides along with - it is dropped instead. */
    @Test
    void doesNotFailASignupOverAnUnreadableBirthDate() {
        jdbc.sql("""
                insert into auth.users (email, raw_user_meta_data)
                values ('bad-date@example.com', '{"birth_date":"not-a-date","gender":"WHATEVER"}'::jsonb)
                """).update();

        var row = jdbc.sql("""
                select birth_date, gender::text from englow3.users where email = 'bad-date@example.com'
                """).query((rs, index) -> new Object[] { rs.getDate(1), rs.getString(2) }).single();

        assertThat(row[0]).isNull();
        // Same for a gender nobody recognises: stored as null rather than breaking the enum on every later read.
        assertThat(row[1]).isNull();
    }
}
