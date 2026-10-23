package com.englow3.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.englow3.ai.api.AiJobQueue;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.support.ExamFixture;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Erasing an account against the real schema. The foreign keys are the point: some cascade, some restrict, and a delete
 * that is correct for one table fails on another - which a mocked repository cannot show.
 */
class AccountServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private AccountService account;

    @Autowired
    private AiJobQueue queue;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private ObjectStorageClient storage;

    private UUID authId;
    private UUID userId;
    private String email;
    private UUID examId;

    /** Signs up through auth.users, so the real sync trigger creates the learner exactly as production does. */
    @BeforeEach
    void signUp() {
        authId = UUID.randomUUID();
        email = "erase-%s@example.com".formatted(authId);
        jdbc.sql("insert into auth.users (id, email) values (:id, :email)").param("id", authId).param("email", email)
                .update();
        userId = jdbc.sql("select id from users where auth_provider_id = :a").param("a", authId).query(UUID.class)
                .single();
        signIn(authId);
        examId = new ExamFixture(jdbc).publishedExam("Erase Test " + authId, new LearnerFixture(jdbc).learner());
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        jdbc.sql("delete from attempt_section_results where exam_attempt_id in "
                + "(select id from exam_attempts where exam_id = :e)").param("e", examId).update();
        jdbc.sql("delete from exam_attempts where exam_id = :e").param("e", examId).update();
        jdbc.sql("delete from exams where id = :e").param("e", examId).update();
        jdbc.sql("delete from ai_jobs where requested_by_user_id = :u").param("u", userId).update();
        jdbc.sql("delete from users where id = :u").param("u", userId).update();
        jdbc.sql("delete from auth.users where id = :a").param("a", authId).update();
    }

    private void signIn(UUID subject) {
        Jwt token = Jwt.withTokenValue("test").header("alg", "none").subject(subject.toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("ROLE_LEARNER"))));
    }

    private UUID sit(UUID learner) {
        UUID attempt = UUID.randomUUID();
        jdbc.sql("""
                insert into exam_attempts (id, exam_id, exam_version_number, user_id, status, started_at, expires_at,
                                           max_raw_score, question_count, score_percentage, mode)
                values (:id, :exam, 1, :user, 'SCORED', now(), now() + interval '1 hour', 100, 10, 50, 'FULL')
                """).param("id", attempt).param("exam", examId).param("user", learner).update();
        jdbc.sql("""
                insert into attempt_section_results (id, exam_attempt_id, section_type, max_raw_score, question_count)
                values (:id, :attempt, 'LISTENING', 100, 10)
                """).param("id", UUID.randomUUID()).param("attempt", attempt).update();
        return attempt;
    }

    private long count(String table, String column, UUID id) {
        return jdbc.sql("select count(*) from " + table + " where " + column + " = :id").param("id", id)
                .query(Long.class).single();
    }

    /** Foreign keys that restrict (attempts and their rows) and ones that cascade, in one erasure. */
    @Test
    void removesTheLearnerTheirAttemptsAndTheirSignIn() {
        sit(userId);
        jdbc.sql("""
                insert into user_subscriptions (user_id, plan, billing_period, current_period_end)
                values (:u, 'PRO', 'MONTHLY', now() + interval '10 days')
                """).param("u", userId).update();
        jdbc.sql("update users set avatar_object_key = 'me.png' where id = :u").param("u", userId).update();

        account.erase(email);

        assertThat(count("users", "id", userId)).isZero();
        assertThat(count("auth.users", "id", authId)).isZero();
        assertThat(count("exam_attempts", "user_id", userId)).isZero();
        assertThat(count("user_subscriptions", "user_id", userId)).isZero();
        verify(storage).delete(anyString(), eq("me.png"));
    }

    @Test
    void leavesOtherPeopleAndSharedContentAlone() {
        UUID other = new LearnerFixture(jdbc).learner();
        sit(other);
        sit(userId);

        account.erase(email);

        assertThat(count("exam_attempts", "user_id", other)).isEqualTo(1);
        assertThat(count("exams", "id", examId)).isEqualTo(1);
        assertThat(count("users", "id", other)).isEqualTo(1);
    }

    @Test
    void refusesAnEmailThatIsNotTheAccounts() {
        sit(userId);

        assertThatThrownBy(() -> account.erase("someone-else@example.com")).isInstanceOf(BadRequestException.class);

        assertThat(count("users", "id", userId)).isEqualTo(1);
        assertThat(count("auth.users", "id", authId)).isEqualTo(1);
        verify(storage, never()).delete(anyString(), anyString());
    }

    @Test
    void ignoresCaseAndSurroundingSpacesInTheConfirmation() {
        account.erase("  " + email.toUpperCase() + " ");

        assertThat(count("users", "id", userId)).isZero();
    }

    /** A running job still reads these rows; erasing under it would fail the job half-way. */
    @Test
    void waitsForAnAiJobThatIsStillRunning() {
        UUID target = UUID.randomUUID();
        queue.enqueueProductiveAssessment(target, "{}", "erase:" + target, "v1", userId);

        assertThatThrownBy(() -> account.erase(email)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("still running");

        assertThat(count("users", "id", userId)).isEqualTo(1);
    }

    /** Staff own published content through foreign keys; their removal is an administrator's decision. */
    @Test
    void doesNotLetStaffEraseThemselves() {
        jdbc.sql("update users set role = 'STAFF' where id = :u").param("u", userId).update();

        assertThatThrownBy(() -> account.erase(email)).isInstanceOf(ConflictException.class);

        assertThat(count("users", "id", userId)).isEqualTo(1);
    }

    @Test
    void exportsOnlyTheCallersOwnRecords() {
        UUID other = new LearnerFixture(jdbc).learner();
        sit(other);
        sit(userId);
        sit(userId);

        Map<String, JsonNode> data = account.exportData();

        assertThat(data.get("account").get(0).get("email").asText()).isEqualTo(email);
        assertThat(data.get("examAttempts")).hasSize(2);
        data.get("examAttempts").forEach(row -> assertThat(row.get("user_id").asText()).isEqualTo(userId.toString()));
        assertThat(data.get("subscription")).isEmpty();
        assertThat(data).containsKeys("flashcardReviews", "dictationAttempts", "tutorMessages", "assessmentAttempts");
    }
}
