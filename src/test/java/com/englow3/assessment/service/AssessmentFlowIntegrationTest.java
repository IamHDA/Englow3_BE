package com.englow3.assessment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.englow3.assessment.dto.command.AssessmentTaskCommand;
import com.englow3.assessment.entity.AssessmentAttemptStatus;
import com.englow3.assessment.entity.AssessmentSkill;
import com.englow3.progress.query.DailyPathQuery;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssessmentFlowIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    AssessmentService learner;
    @Autowired
    AssessmentAuthoringService authoring;
    @Autowired
    AssessmentNotificationService notifications;
    @Autowired
    DailyPathQuery progress;
    @Autowired
    JdbcClient jdbc;
    @MockitoBean
    ObjectStorageClient storage;
    UUID staff, admin, user, other, task, attempt;

    @BeforeEach
    void setup() {
        var fixture = new LearnerFixture(jdbc);
        staff = fixture.learner();
        admin = fixture.learner();
        user = fixture.learner();
        other = fixture.learner();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (attempt != null) {
            jdbc.sql("delete from assessment_reviews where attempt_id=:id").param("id", attempt).update();
            jdbc.sql("delete from assessment_attempts where id=:id").param("id", attempt).update();
        }
        if (task != null) {
            jdbc.sql("delete from assessment_tasks where id=:id").param("id", task).update();
        }
        for (UUID id : List.of(staff, admin, user, other)) {
            jdbc.sql("delete from users where id=:id").param("id", id).update();
        }
    }

    @ParameterizedTest
    @EnumSource(AssessmentSkill.class)
    void allThreeRolesCanCompleteBothSkillsWithoutProviderCredentials(AssessmentSkill skill) throws Exception {
        when(storage.presignPut(anyString(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(java.net.URI.create("http://localhost:9000/test-upload").toURL());
        when(storage.presignGet(anyString(), anyString(), any()))
                .thenReturn(java.net.URI.create("http://localhost:9000/test-download").toURL());
        when(storage.metadata(anyString(), anyString()))
                .thenReturn(new ObjectStorageClient.StoredObjectMetadata(100L, "audio/wav"));
        signIn(staff, "STAFF");
        var created = authoring.create(new AssessmentTaskCommand(skill, "Opinion task",
                skill == AssessmentSkill.WRITING ? "TASK_2" : "PART_2", "Discuss the advantages and disadvantages.",
                "Use specific examples.", "Sample answer", 250, 2400));
        task = created.id();
        authoring.transition(task, "submit", null);
        assertThatThrownBy(() -> authoring.transition(task, "approve", null)).isInstanceOf(ForbiddenException.class);
        signIn(user, "LEARNER");
        assertThat(learner.catalog(null, PageRequest.of(0, 100)).getContent()).noneMatch(t -> t.id().equals(task));
        signIn(admin, "ADMIN");
        authoring.transition(task, "approve", null);
        signIn(user, "LEARNER");
        assertThat(learner.task(task).sampleAnswer()).isNull();
        UUID clientKey = UUID.randomUUID();
        var draft = learner.start(task, clientKey, skill == AssessmentSkill.SPEAKING ? "audio/wav" : null,
                skill == AssessmentSkill.SPEAKING ? 100L : null).attempt();
        attempt = draft.id();
        assertThat(draft.createdAt()).isNotNull();
        assertThat(learner.start(task, clientKey, null, null).attempt().id()).isEqualTo(attempt);
        if (skill == AssessmentSkill.WRITING) {
            learner.saveDraft(attempt,
                    "Learning English every day helps students develop skills and discover new opportunities. Consistent practice creates confidence and improves communication across many situations.",
                    draft.version());
            assertThatThrownBy(() -> learner.saveDraft(attempt, "Another tab", draft.version()))
                    .isInstanceOf(ConflictException.class);
        }
        var submitted = learner.submit(attempt);
        assertThat(submitted.status()).isEqualTo(AssessmentAttemptStatus.NEEDS_REVIEW);
        assertThat(submitted.task().sampleAnswer()).isNull();
        signIn(other, "LEARNER");
        assertThatThrownBy(() -> learner.attempt(attempt)).isInstanceOf(NotFoundException.class);
        signIn(other, "STAFF");
        assertThatThrownBy(() -> authoring.submission(attempt)).isInstanceOf(ForbiddenException.class);
        signIn(staff, "STAFF");
        String report = """
                {"criteria":[{"key":"TASK_RESPONSE","score":6,"feedback":"An argument is present"},{"key":"COHERENCE_COHESION","score":6,"feedback":"Ideas connect"},{"key":"LEXICAL_RESOURCE","score":6,"feedback":"Vocabulary is clear"},{"key":"GRAMMATICAL_RANGE","score":6,"feedback":"Sentences are grammatical"}],"summary":"Practice more examples","strengths":["Clear ideas"],"improvements":["Expand support"]}
                """;
        if (skill == AssessmentSkill.SPEAKING) {
            report = report.replace("TASK_RESPONSE", "FLUENCY_COHERENCE").replace("COHERENCE_COHESION",
                    "PRONUNCIATION");
        }
        var graded = authoring.grade(attempt, report, "Reviewed the full response",
                skill == AssessmentSkill.SPEAKING ? "Transcribed response" : null);
        assertThat(graded.source()).isEqualTo("HUMAN");
        signIn(user, "LEARNER");
        assertThat(learner.attempt(attempt).task().sampleAnswer()).isEqualTo("Sample answer");
        assertThat(new ObjectMapper().readTree(learner.attempt(attempt).report()).path("overall").asInt()).isEqualTo(6);
        // The learner reads the overall band and the feedback, but not the band of each criterion.
        assertThat(learner.attempt(attempt).report()).doesNotContain("\"score\"").contains("An argument is present");
        assertThat(graded.report()).contains("\"score\"");
        assertThat(jdbc.sql("select count(*) from assessment_reviews where attempt_id=:id").param("id", attempt)
                .query(Integer.class).single()).isEqualTo(1);
        var before = progress.activityTotals(user, Instant.EPOCH).productiveAttempts();
        assertThat(notifications.unread(0).getContent()).anyMatch(n -> n.attemptId().equals(attempt));
        notifications.markRead(attempt, graded.version());
        assertThat(notifications.unread(0).getContent()).noneMatch(n -> n.attemptId().equals(attempt));
        assertThat(learner.attempt(attempt).version()).isEqualTo(graded.version());
        signIn(other, "LEARNER");
        assertThatThrownBy(() -> notifications.markRead(attempt, graded.version()))
                .isInstanceOf(NotFoundException.class);
        signIn(admin, "ADMIN");
        var corrected = authoring.grade(attempt, report.replace("\"score\":6", "\"score\":7"), "Corrected after review",
                null, graded.version());
        signIn(user, "LEARNER");
        assertThat(notifications.unread(0).getContent())
                .anyMatch(n -> n.attemptId().equals(attempt) && n.version() == corrected.version());
        assertThatThrownBy(() -> notifications.markRead(attempt, graded.version()))
                .isInstanceOf(ConflictException.class);
        notifications.markRead(attempt, corrected.version());
        assertThat(progress.activityTotals(user, Instant.EPOCH).productiveAttempts()).isEqualTo(before);
        signIn(staff, "STAFF");
        var queue = authoring.searchSubmissions(null, skill, "Opinion", true, PageRequest.of(0, 100));
        assertThat(queue.getSize()).isEqualTo(50);
        assertThat(queue.getContent()).anyMatch(s -> s.id().equals(attempt) && s.learnerId().equals(user));
        signIn(other, "STAFF");
        assertThat(authoring.searchSubmissions(null, skill, "Opinion", true, PageRequest.of(0, 12)).getContent())
                .noneMatch(s -> s.id().equals(attempt));
    }

    private void signIn(UUID id, String role) {
        UUID authId = jdbc.sql("select auth_provider_id from users where id=:id").param("id", id).query(UUID.class)
                .single();
        Jwt token = Jwt.withTokenValue("test").header("alg", "none").subject(authId.toString()).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
