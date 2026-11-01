package com.englow3.roadmap.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.ai.api.AiJobHandler.Outcome;
import com.englow3.ai.client.LlmClient;
import com.englow3.roadmap.dto.result.RoadmapResult;
import com.englow3.roadmap.entity.LevelCheckPurpose;
import com.englow3.roadmap.worker.RoadmapGenerationHandler;
import com.englow3.shared.time.StudyCalendar;
import com.englow3.support.ExamFixture;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A roadmap from the request to the finish, against the real schema: the plan goes out as a queued job with everything
 * the generator needs, a plan that keeps the rules is written, progress comes from activity, and the milestone - a mock
 * paper or the level check - moves the plan on. The model is the one thing stubbed.
 */
class RoadmapFlowIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private RoadmapService roadmapService;
    @Autowired
    private LevelCheckService levelCheckService;
    @Autowired
    private RoadmapPlanWriter writer;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private StudyCalendar calendar;

    private final LlmClient llm = mock(LlmClient.class);
    private RoadmapGenerationHandler handler;
    private LearnerFixture fixture;
    private UUID learner;
    private UUID quiz;

    @BeforeEach
    void setUp() {
        handler = new RoadmapGenerationHandler(llm, writer, mapper);
        fixture = new LearnerFixture(jdbc);
        learner = fixture.learner();
        quiz = fixture.publishedQuiz("Thì hiện tại đơn " + learner, learner);
        jdbc.sql("update quizzes set target_level = 'A2' where id = :id").param("id", quiz).update();
        SignedIn.as(jdbc, learner);
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    @Test
    void aLevelGoalIsPlannedThenClosedByTheLevelCheck() throws Exception {
        profile("A2", null, null, "B1");

        RoadmapResult requested = roadmapService.create();
        assertThat(requested.status()).isEqualTo("GENERATING");
        assertThat(roadmapService.create().id()).as("asking again for the same goal is the same plan")
                .isEqualTo(requested.id());

        LocalDate today = calendar.today();
        answerWith("""
                {"stages": [{"title": "Lên B1", "fromLevel": "A2", "toLevel": "B1",
                  "plannedStart": "%s", "plannedEnd": "%s",
                  "items": [{"kind": "QUIZ", "contentId": "%s", "plannedWeek": 1, "targetPercent": 80}],
                  "milestone": {}}]}
                """.formatted(today, today.plusWeeks(6), quiz));
        runJob(requested.id());

        RoadmapResult active = roadmapService.current().orElseThrow();
        assertThat(active.status()).isEqualTo("ACTIVE");
        RoadmapResult.Stage stage = active.stages().getFirst();
        assertThat(stage.status()).isEqualTo("CURRENT");
        assertThat(stage.items()).singleElement().satisfies(item -> assertThat(item.done()).isFalse());
        assertThat(stage.milestone().kind()).isEqualTo("LEVEL_CHECK");
        assertThat(stage.milestone().ready()).isFalse();

        fixture.quizAttempt(learner, quiz, 90, true, Instant.now());
        assertThat(roadmapService.current().orElseThrow().stages().getFirst().milestone().ready()).isTrue();

        UUID paper = levelCheckPaper();
        var check = levelCheckService.start(LevelCheckPurpose.MILESTONE, stage.milestone().id());
        assertThat(check.examId()).isEqualTo(paper);
        assertThat(levelCheckService.start(LevelCheckPurpose.MILESTONE, stage.milestone().id()).id())
                .isEqualTo(check.id());

        // No microphone: the written parts make the whole result.
        assertThat(levelCheckService.skipSpeaking(check.id()).speakingSkipped()).isTrue();
        // Reading 2/2 is C2 and listening 1/2 is B1: (6x40 + 3x30) / 70 = 4.7, rounded down to B2.
        var done = levelCheckService.attachExamAttempt(check.id(), sitting(paper, 2, 1));
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.vocabLevel()).isEqualTo("C2");
        assertThat(done.listeningLevel()).isEqualTo("B1");
        assertThat(done.resultLevel()).isEqualTo("B2");

        RoadmapResult finished = roadmapService.current().orElseThrow();
        assertThat(finished.status()).isEqualTo("COMPLETED");
        assertThat(finished.stages().getFirst().status()).isEqualTo("PASSED");
        assertThat(finished.stages().getFirst().milestone().resultLevel()).isEqualTo("B2");
        assertThat(jdbc.sql("select current_level from learner_profiles where user_id = :id").param("id", learner)
                .query(String.class).single()).isEqualTo("B2");
    }

    @Test
    void aPlanThatBreaksTheRulesIsAskedForAgainAndNeverWritten() throws Exception {
        profile("A2", null, null, "B1");
        RoadmapResult requested = roadmapService.create();
        answerWith("""
                {"stages": [{"fromLevel": "A2", "toLevel": "B1", "plannedStart": "%s", "plannedEnd": "%s",
                  "items": [{"kind": "QUIZ", "contentId": "%s", "plannedWeek": 1}], "milestone": {}}]}
                """.formatted(calendar.today(), calendar.today().plusWeeks(4), UUID.randomUUID()));

        Outcome outcome = handler.run(UUID.randomUUID(), requested.id(), payload(requested.id()));

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.retryable()).isTrue();
        assertThat(outcome.errorCode()).isEqualTo("ROADMAP_PLAN_INVALID");
        handler.onGaveUp(requested.id(), outcome.errorCode());
        assertThat(roadmapService.current().orElseThrow().status()).isEqualTo("FAILED");
        assertThat(roadmapService.create().status()).as("a failed plan can be asked for again").isEqualTo("GENERATING");
    }

    @Test
    void aFailedMockAsksForAReplanAndAPassMovesToTheNextStage() throws Exception {
        profile("B1", "IELTS", new BigDecimal("6.5"), null);
        purpose("CERTIFICATE");
        UUID mock = new ExamFixture(jdbc).publishedExam("IELTS mock " + learner, learner);
        jdbc.sql("update exams set certificate_type = 'IELTS' where id = :id").param("id", mock).update();
        RoadmapResult requested = roadmapService.create();
        LocalDate today = calendar.today();
        answerWith("""
                {"stages": [
                  {"fromLevel": "B1", "toLevel": "B2", "plannedStart": "%s", "plannedEnd": "%s",
                   "items": [{"kind": "QUIZ", "contentId": "%s", "plannedWeek": 1}],
                   "milestone": {"examId": "%s", "requiredScore": 5.5}},
                  {"fromLevel": "B2", "toLevel": "B2", "plannedStart": "%s", "plannedEnd": "%s",
                   "items": [{"kind": "QUIZ", "contentId": "%s", "plannedWeek": 1}],
                   "milestone": {"examId": "%s", "requiredScore": 6.5}}]}
                """.formatted(today, today.plusWeeks(4), quiz, mock, today.plusWeeks(4).plusDays(1),
                today.plusWeeks(10), quiz, mock));
        runJob(requested.id());
        UUID milestone = roadmapService.current().orElseThrow().stages().getFirst().milestone().id();

        RoadmapResult afterFail = roadmapService.recordMockSitting(milestone, mockSitting(mock, "5.0"));
        assertThat(afterFail.stages().getFirst().milestone().status()).isEqualTo("FAILED");
        assertThat(afterFail.adjusting()).isTrue();
        assertThat(afterFail.pendingReason()).isEqualTo("MILESTONE_FAILED");

        RoadmapResult afterPass = roadmapService.recordMockSitting(milestone, mockSitting(mock, "6.0"));
        assertThat(afterPass.adjusting()).as("a pass makes the replan moot").isFalse();
        assertThat(afterPass.stages()).extracting(RoadmapResult.Stage::status).containsExactly("PASSED", "CURRENT");
        assertThat(afterPass.adjustments()).extracting(RoadmapResult.Adjustment::reason).contains("MILESTONE_PASSED");
        assertThatThrownBy(() -> roadmapService.recordMockSitting(milestone, mockSitting(mock, "7.0")))
                .hasFieldOrPropertyWithValue("code", "MILESTONE_NOT_CURRENT");
    }

    @Test
    void aDifferentGoalSupersedesThePlanAndAbandoningEndsIt() {
        profile("A2", null, null, "B1");
        RoadmapResult first = roadmapService.create();
        purpose("CERTIFICATE");
        jdbc.sql("update learner_profiles set target_level = null, target_certificate_type = 'IELTS', "
                + "target_score = 6.0 where user_id = :id").param("id", learner).update();
        UUID mock = new ExamFixture(jdbc).publishedExam("IELTS mock " + learner, learner);
        jdbc.sql("update exams set certificate_type = 'IELTS' where id = :id").param("id", mock).update();

        RoadmapResult second = roadmapService.create();

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(jdbc.sql("select status from learning_roadmaps where id = :id").param("id", first.id())
                .query(String.class).single()).isEqualTo("SUPERSEDED");
        roadmapService.abandon();
        assertThat(roadmapService.current().orElseThrow().status()).isEqualTo("ABANDONED");
    }

    private void profile(String current, String certificate, BigDecimal targetScore, String targetLevel) {
        jdbc.sql("""
                insert into learner_profiles (id, user_id, current_level, target_certificate_type, target_score,
                                              target_level, target_date, daily_study_minutes)
                values (:id, :user, :current, :certificate, :score, :level, :date, 60)
                """).param("id", UUID.randomUUID()).param("user", learner).param("current", current)
                .param("certificate", certificate).param("score", targetScore).param("level", targetLevel)
                .param("date", calendar.today().plusMonths(8)).update();
    }

    private void purpose(String code) {
        jdbc.sql("""
                insert into user_learning_purposes (user_id, learning_purpose_id)
                select :user, id from learning_purposes where purpose_code = :code
                """).param("user", learner).param("code", code).update();
    }

    private void answerWith(String plan) throws Exception {
        String raw = mapper.writeValueAsString(java.util.Map.of("content", plan, "model", "stub-model"));
        when(llm.generateStructured(anyString(), anyString(), anyInt())).thenReturn(raw);
    }

    private String payload(UUID roadmapId) {
        return jdbc.sql("""
                select input_payload from ai_jobs where target_id = :id and job_type = 'ROADMAP_GENERATION'
                 order by created_at desc limit 1
                """).param("id", roadmapId).query(String.class).single();
    }

    /** What the worker does: run outside a transaction, then complete under the claim. */
    private void runJob(UUID roadmapId) {
        String payload = payload(roadmapId);
        Outcome outcome = handler.run(UUID.randomUUID(), roadmapId, payload);
        assertThat(outcome.success()).as(outcome.errorMessage()).isTrue();
        handler.complete(roadmapId, payload, outcome.outputPayload());
    }

    private UUID levelCheckPaper() {
        ExamFixture exams = new ExamFixture(jdbc);
        UUID paper = exams.publishedExam("Level check " + learner, learner);
        jdbc.sql("update exams set exam_type = 'LEVEL_CHECK', published_at = now() where id = :id").param("id", paper)
                .update();
        UUID reading = exams.questionSet(exams.part(exams.section(paper, 1, "READING"), 1, "Grammar"), 1);
        UUID listening = exams.questionSet(exams.part(exams.section(paper, 2, "LISTENING"), 1, "Listening"), 1);
        exams.question(reading, 1, "r1");
        exams.question(reading, 2, "r2");
        exams.question(listening, 1, "l1");
        exams.question(listening, 2, "l2");
        return paper;
    }

    /**
     * A scored sitting with the first {@code readingRight} reading and {@code listeningRight} listening answers right.
     */
    private UUID sitting(UUID paper, int readingRight, int listeningRight) {
        UUID attempt = insertAttempt(paper, null);
        jdbc.sql("""
                insert into attempt_answers (id, exam_attempt_id, question_id, is_correct, awarded_raw_score,
                                             grading_status, answered_at, graded_at)
                select gen_random_uuid(), :attempt, q.id, true, 1, 'GRADED', now(), now()
                  from questions q
                  join question_sets qs on qs.id = q.question_set_id
                  join section_parts sp on sp.id = qs.section_part_id
                  join exam_sections es on es.id = sp.exam_section_id
                 where es.exam_id = :paper
                   and ((es.section_type = 'READING' and q.order_no <= :reading)
                     or (es.section_type = 'LISTENING' and q.order_no <= :listening))
                """).param("attempt", attempt).param("paper", paper).param("reading", readingRight)
                .param("listening", listeningRight).update();
        return attempt;
    }

    private UUID mockSitting(UUID paper, String band) {
        return insertAttempt(paper, new BigDecimal(band));
    }

    private UUID insertAttempt(UUID exam, BigDecimal converted) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into exam_attempts (id, exam_id, exam_version_number, user_id, status, started_at, expires_at,
                                           max_raw_score, question_count, score_percentage, converted_score, mode,
                                           scored_at)
                values (:id, :exam, 1, :user, 'SCORED', now(), now() + interval '1 hour', 4, 4, 75, :converted,
                        'FULL', now() + interval '1 second')
                """).param("id", id).param("exam", exam).param("user", learner).param("converted", converted).update();
        return id;
    }
}
