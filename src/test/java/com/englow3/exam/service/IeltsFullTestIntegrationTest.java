package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.exam.dto.command.SaveFullTestCommand;
import com.englow3.exam.dto.command.StartExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand.SubmittedAnswer;
import com.englow3.exam.dto.result.FullTestSittingResult;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.FullTestSittingStatus;
import com.englow3.support.ExamFixture;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;

/**
 * An IELTS full test end to end on the real schema: authored and approved through the review workflow, sat by an IELTS
 * learner still on the level step of onboarding, given a provisional band once Listening and Reading are scored, and an
 * overall band once the five Writing and Speaking answers are graded - each recorded as the learner's placement.
 */
class IeltsFullTestIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private FullTestService fullTests;

    @Autowired
    private FullTestSittingService sittings;

    @Autowired
    private ExamAttemptService attempts;

    @Autowired
    private ExamReviewService review;

    @Autowired
    private com.englow3.assessment.service.AssessmentAuthoringService taskAuthoring;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactions;

    private UUID author;
    private UUID learner;
    private UUID examId;
    private final List<UUID> questions = new ArrayList<>();
    private final List<UUID> tasks = new ArrayList<>();

    @BeforeEach
    void anIeltsPaperFiveTasksAndAnIeltsLearner() {
        LearnerFixture learners = new LearnerFixture(jdbc);
        author = learners.learner();
        learner = learners.learner();
        jdbc.sql("update users set onboarding_step = 'CURRENT_LEVEL' where id = :id").param("id", learner).update();
        jdbc.sql("""
                insert into user_learning_purposes (user_id, learning_purpose_id)
                select :id, id from learning_purposes where purpose_code = 'CERTIFICATE'
                """).param("id", learner).update();
        jdbc.sql("""
                insert into learner_profiles (id, user_id, target_certificate_type)
                values (:id, :user, 'IELTS')
                """).param("id", UUID.randomUUID()).param("user", learner).update();

        ExamFixture exams = new ExamFixture(jdbc);
        examId = exams.publishedExam("IELTS Academic Listening and Reading", author);
        jdbc.sql("""
                update exams set certificate_type = 'IELTS', certificate_variant = 'ACADEMIC', max_raw_score = 80
                 where id = :id
                """).param("id", examId).update();
        for (String section : List.of("LISTENING", "READING")) {
            UUID set = exams.questionSet(exams.part(exams.section(examId, questions.size() + 1, section), 1, "Part"),
                    1);
            for (int number = 1; number <= 40; number++) {
                UUID question = UUID.randomUUID();
                jdbc.sql("""
                        insert into questions (id, question_set_id, question_type, content, difficulty_level,
                                               skill_type, order_no, max_raw_score)
                        values (:id, :set, 'TEXT_ENTRY', 'Gap {{1}}', 'MEDIUM', :skill, :orderNo, 1)
                        """).param("id", question).param("set", set).param("skill", section).param("orderNo", number)
                        .update();
                jdbc.sql("""
                        insert into question_accepted_answers (id, question_id, answer_text, order_no)
                        values (:id, :question, 'museum', 1)
                        """).param("id", UUID.randomUUID()).param("question", question).update();
                questions.add(question);
            }
        }

        for (String[] task : List.of(new String[] { "WRITING", "TASK_1" }, new String[] { "WRITING", "TASK_2" },
                new String[] { "SPEAKING", "PART_1" }, new String[] { "SPEAKING", "PART_2" },
                new String[] { "SPEAKING", "PART_3" })) {
            UUID id = UUID.randomUUID();
            jdbc.sql("""
                    insert into assessment_tasks (id, skill, title, task_type, instructions, time_limit_seconds,
                                                  status, created_by_user_id, certificate_type, image_object_key)
                    values (:id, :skill, :title, :type, 'Answer the task', 1200, 'PUBLISHED', :author, 'IELTS', :chart)
                    """).param("id", id).param("skill", task[0]).param("title", task[0] + " " + task[1])
                    .param("type", task[1]).param("author", author)
                    .param("chart", "TASK_1".equals(task[1]) ? "assessment-tasks/" + id + "/chart.png" : null).update();
            tasks.add(id);
        }
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    @Test
    void aFullTestGoesFromReviewToAnOverallBandAndSetsThePlacement() {
        SignedIn.as(jdbc, author);
        var draft = fullTests
                .save(new SaveFullTestCommand(null, "IELTS Academic Full Test 1", "", CertificateVariant.ACADEMIC,
                        examId, tasks.get(0), tasks.get(1), tasks.get(2), tasks.get(3), tasks.get(4)));
        fullTests.submitForReview(draft.id());
        assertThat(fullTests.approve(draft.id()).status()).isEqualTo(ExamStatus.PUBLISHED);

        UUID milestone = ieltsRoadmapWaitingOn(draft.id(), "6.5");

        SignedIn.as(jdbc, learner);
        FullTestSittingResult sitting = sittings.start(draft.id());
        assertThat(sittings.start(draft.id()).id()).isEqualTo(sitting.id());

        var paper = attempts.start(StartExamAttemptCommand.full(examId));
        List<SubmittedAnswer> answers = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            // 30 of 40 right in each section: band 7.0 in both.
            answers.add(new SubmittedAnswer(questions.get(i), List.of(), i % 40 < 30 ? "museum" : "musuem"));
        }
        attempts.submit(new SubmitExamAttemptCommand(paper.id(), answers));

        FullTestSittingResult provisional = sittings.view(sitting.id());
        assertThat(provisional.listeningBand()).isEqualByComparingTo("7.0");
        assertThat(provisional.readingBand()).isEqualByComparingTo("7.0");
        assertThat(provisional.provisionalBand()).isEqualByComparingTo("7.0");
        assertThat(provisional.status()).isEqualTo(FullTestSittingStatus.IN_PROGRESS);
        assertThat(profileScore()).isEqualByComparingTo("7.0");
        assertThat(onboardingStep()).isEqualTo("LEARNING_GOAL");

        for (UUID task : tasks) {
            gradedAnswer(task, "6.0");
        }
        FullTestSittingResult done = sittings.view(sitting.id());

        assertThat(done.status()).isEqualTo(FullTestSittingStatus.COMPLETED);
        assertThat(done.writingBand()).isEqualByComparingTo("6.0");
        assertThat(done.speakingBand()).isEqualByComparingTo("6.0");
        assertThat(done.overallBand()).isEqualByComparingTo("6.5");
        assertThat(profileScore()).isEqualByComparingTo("6.5");
        assertThat(jdbc.sql("select current_level from learner_profiles where user_id = :u").param("u", learner)
                .query(String.class).single()).isEqualTo("B2");
        // The roadmap heard FullTestCompleted after the view committed: its IELTS mock milestone took the band.
        assertThat(jdbc.sql("select status from roadmap_milestones where id = :id").param("id", milestone)
                .query(String.class).single()).isEqualTo("PASSED");
        assertThat(jdbc.sql("select result_score from roadmap_milestones where id = :id").param("id", milestone)
                .query(java.math.BigDecimal.class).single()).isEqualByComparingTo("6.5");
    }

    private UUID draftFullTest() {
        return fullTests.save(new SaveFullTestCommand(null, "Full test safety", "", CertificateVariant.ACADEMIC, examId,
                tasks.get(0), tasks.get(1), tasks.get(2), tasks.get(3), tasks.get(4))).id();
    }

    @Test
    void refusesAStaleFullTestEditorWithoutOverwritingTheNewerDraft() {
        SignedIn.as(jdbc, author, "ADMIN");
        UUID id = draftFullTest();
        var original = fullTests.detail(id);
        var saved = fullTests.save(new SaveFullTestCommand(id, "Newer title", "", CertificateVariant.ACADEMIC, examId,
                tasks.get(0), tasks.get(1), tasks.get(2), tasks.get(3), tasks.get(4), original.version()));
        assertThatThrownBy(
                () -> fullTests.save(new SaveFullTestCommand(id, "Stale title", "", CertificateVariant.ACADEMIC, examId,
                        tasks.get(0), tasks.get(1), tasks.get(2), tasks.get(3), tasks.get(4), original.version())))
                                .isInstanceOf(com.englow3.shared.error.ConflictException.class);
        assertThat(fullTests.detail(id).title()).isEqualTo(saved.title());
    }

    @Test
    void reviewRejectsConversionTablesWithAMissingMiddleRange() {
        SignedIn.as(jdbc, author, "ADMIN");
        transactions.executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            UUID fullTest = draftFullTest();
            jdbc.sql(
                    "delete from score_conversions where certificate_type='IELTS' and section_type='LISTENING' and raw_score_from=18")
                    .update();
            assertThatThrownBy(() -> fullTests.submitForReview(fullTest)).hasFieldOrPropertyWithValue("code",
                    "FULL_TEST_PAPER_NOT_BANDABLE");
        });
    }

    @Test
    void reviewRejectsAPaperWithoutFortyWholeMarksPerSkill() {
        SignedIn.as(jdbc, author, "ADMIN");
        UUID fullTest = draftFullTest();
        jdbc.sql("update questions set max_raw_score=0.5 where id=:id").param("id", questions.getFirst()).update();
        assertThatThrownBy(() -> fullTests.submitForReview(fullTest)).hasFieldOrPropertyWithValue("code",
                "FULL_TEST_PAPER_NOT_BANDABLE");
        assertThat(fullTests.detail(fullTest).status()).isEqualTo(ExamStatus.DRAFT);
    }

    @Test
    void componentsCannotBeArchivedUntilTheFullTestIsArchived() {
        SignedIn.as(jdbc, author, "ADMIN");
        UUID fullTest = draftFullTest();
        fullTests.submitForReview(fullTest);
        assertThatThrownBy(() -> review.archive(new com.englow3.exam.dto.command.ArchiveExamCommand(examId)))
                .hasFieldOrPropertyWithValue("code", "FULL_TEST_COMPONENT_IN_USE");
        fullTests.approve(fullTest);
        assertThatThrownBy(() -> taskAuthoring.transition(tasks.getFirst(), "archive", null))
                .hasFieldOrPropertyWithValue("code", "FULL_TEST_COMPONENT_IN_USE");
        fullTests.archive(fullTest);
        assertThat(review.archive(new com.englow3.exam.dto.command.ArchiveExamCommand(examId)).status())
                .isEqualTo(ExamStatus.ARCHIVED);
        taskAuthoring.transition(tasks.getFirst(), "archive", null);
    }

    @Test
    void archivedFullTestsStillProtectTheirLearnersOpenSittings() {
        SignedIn.as(jdbc, author, "ADMIN");
        UUID fullTest = draftFullTest();
        fullTests.submitForReview(fullTest);
        fullTests.approve(fullTest);
        SignedIn.as(jdbc, learner);
        sittings.start(fullTest);
        SignedIn.as(jdbc, author, "ADMIN");
        fullTests.archive(fullTest);
        assertThatThrownBy(() -> review.archive(new com.englow3.exam.dto.command.ArchiveExamCommand(examId)))
                .hasFieldOrPropertyWithValue("code", "FULL_TEST_COMPONENT_IN_USE");
        assertThatThrownBy(() -> taskAuthoring.transition(tasks.getFirst(), "archive", null))
                .hasFieldOrPropertyWithValue("code", "FULL_TEST_COMPONENT_IN_USE");
    }

    @Test
    void olderPublishedFullTestsAreRecheckedBeforeStarting() {
        SignedIn.as(jdbc, author, "ADMIN");
        UUID fullTest = draftFullTest();
        fullTests.submitForReview(fullTest);
        fullTests.approve(fullTest);
        // A legacy inconsistency predating the archive guard.
        jdbc.sql("update assessment_tasks set status='ARCHIVED' where id=:id").param("id", tasks.getFirst()).update();
        SignedIn.as(jdbc, learner);
        assertThatThrownBy(() -> sittings.start(fullTest)).hasFieldOrPropertyWithValue("code",
                "FULL_TEST_TASK_INVALID");
    }

    /** An active IELTS roadmap whose current stage closes with a mock milestone on this full test. */
    private UUID ieltsRoadmapWaitingOn(UUID fullTestId, String requiredScore) {
        UUID roadmap = UUID.randomUUID();
        UUID stage = UUID.randomUUID();
        UUID milestone = UUID.randomUUID();
        jdbc.sql("""
                insert into learning_roadmaps (id, user_id, status, goal_type, target_certificate, target_score,
                                               start_level)
                values (:id, :user, 'ACTIVE', 'CERTIFICATE', 'IELTS', 7.0, 'B1')
                """).param("id", roadmap).param("user", learner).update();
        jdbc.sql("""
                insert into roadmap_stages (id, roadmap_id, order_no, title, from_level, to_level, planned_start,
                                            planned_end, status, created_at)
                values (:id, :roadmap, 1, 'Stage 1', 'B1', 'B2', current_date, current_date + 30, 'CURRENT',
                        now() - interval '1 hour')
                """).param("id", stage).param("roadmap", roadmap).update();
        jdbc.sql("""
                insert into roadmap_stages (id, roadmap_id, order_no, title, from_level, to_level, planned_start,
                                            planned_end, status)
                values (:id, :roadmap, 2, 'Stage 2', 'B2', 'C1', current_date + 31, current_date + 60, 'LOCKED')
                """).param("id", UUID.randomUUID()).param("roadmap", roadmap).update();
        jdbc.sql("""
                insert into roadmap_milestones (id, stage_id, kind, full_test_id, required_score, status)
                values (:id, :stage, 'CERTIFICATE_MOCK', :fullTest, cast(:score as numeric), 'PENDING')
                """).param("id", milestone).param("stage", stage).param("fullTest", fullTestId)
                .param("score", requiredScore).update();
        return milestone;
    }

    private void gradedAnswer(UUID task, String band) {
        String skill = jdbc.sql("select skill from assessment_tasks where id = :id").param("id", task)
                .query(String.class).single();
        jdbc.sql("""
                insert into assessment_attempts (id, user_id, task_id, client_key, skill, task_snapshot, answer_text,
                                                 audio_object_key, status, report, created_at, submitted_at,
                                                 assessed_at)
                values (:id, :user, :task, :key, :skill, '{}'::jsonb, 'An answer', :audio, 'COMPLETED',
                        cast(:report as jsonb), now(), now(), now())
                """).param("id", UUID.randomUUID()).param("user", learner).param("task", task)
                .param("key", UUID.randomUUID()).param("skill", skill)
                .param("audio", "SPEAKING".equals(skill) ? "recordings/" + UUID.randomUUID() + ".wav" : null)
                .param("report", "{\"overall\": " + band + "}").update();
    }

    private java.math.BigDecimal profileScore() {
        return jdbc.sql("select current_score from learner_profiles where user_id = :u").param("u", learner)
                .query(java.math.BigDecimal.class).single();
    }

    private String onboardingStep() {
        return jdbc.sql("select onboarding_step from users where id = :u").param("u", learner).query(String.class)
                .single();
    }
}
