package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.englow3.exam.api.ScoredExamAttemptReader;
import com.englow3.exam.api.ScoredExamAttemptReader.SectionScore;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.repository.ExamRepository;
import com.englow3.support.ExamFixture;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

class ScoredExamAttemptReaderIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ScoredExamAttemptReader reader;

    @Autowired
    private ExamRepository examRepo;

    @Test
    void scoresEachSectionAgainstEveryQuestionItHolds() {
        UUID learner = new LearnerFixture(jdbc).learner();
        ExamFixture exams = new ExamFixture(jdbc);
        UUID exam = exams.publishedExam("Level check", learner);
        UUID reading = exams.questionSet(exams.part(exams.section(exam, 1, "READING"), 1, "Grammar"), 1);
        UUID listening = exams.questionSet(exams.part(exams.section(exam, 2, "LISTENING"), 1, "Listening"), 1);
        UUID r1 = exams.question(reading, 1, "r1");
        UUID r2 = exams.question(reading, 2, "r2");
        exams.question(reading, 3, "r3");
        UUID l1 = exams.question(listening, 1, "l1");
        exams.question(listening, 2, "l2");
        UUID attempt = scoredAttempt(exam, learner);
        answer(attempt, r1, "1");
        answer(attempt, r2, "0");
        answer(attempt, l1, "1");

        var sitting = reader.read(attempt, learner).orElseThrow();

        assertThat(sitting.scored()).isTrue();
        assertThat(sitting.full()).isTrue();
        assertThat(sitting.sections()).extracting(SectionScore::sectionType).containsExactly("READING", "LISTENING");
        assertThat(sitting.sections().get(0).awarded()).isEqualByComparingTo("1");
        assertThat(sitting.sections().get(0).max()).isEqualByComparingTo("3");
        assertThat(sitting.sections().get(1).awarded()).isEqualByComparingTo("1");
        assertThat(sitting.sections().get(1).max()).isEqualByComparingTo("2");
    }

    @Test
    void showsNobodyElsesSitting() {
        LearnerFixture learners = new LearnerFixture(jdbc);
        UUID owner = learners.learner();
        UUID exam = new ExamFixture(jdbc).publishedExam("Mock", owner);

        assertThat(reader.read(scoredAttempt(exam, owner), learners.learner())).isEmpty();
    }

    @Test
    void keepsLevelChecksOutOfTheMockExamLibrary() {
        UUID author = new LearnerFixture(jdbc).learner();
        UUID exam = new ExamFixture(jdbc).publishedExam("Hidden level check", author);
        jdbc.sql("update exams set exam_type = 'LEVEL_CHECK' where id = :id").param("id", exam).update();

        var catalogue = examRepo.searchCatalogue(ExamStatus.PUBLISHED, null, null, null, null, "Hidden level check",
                PageRequest.of(0, 10));

        assertThat(catalogue.getContent()).isEmpty();
    }

    private UUID scoredAttempt(UUID exam, UUID learner) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into exam_attempts (id, exam_id, exam_version_number, user_id, status, started_at, expires_at,
                                           max_raw_score, question_count, score_percentage, mode, scored_at)
                values (:id, :exam, 1, :user, 'SCORED', now(), now() + interval '1 hour', 5, 5, 40, 'FULL', now())
                """).param("id", id).param("exam", exam).param("user", learner).update();
        return id;
    }

    private void answer(UUID attempt, UUID question, String awarded) {
        jdbc.sql("""
                insert into attempt_answers (id, exam_attempt_id, question_id, is_correct, awarded_raw_score,
                                             grading_status, answered_at, graded_at)
                values (:id, :attempt, :question, :correct, :awarded, 'GRADED', now(), now())
                """).param("id", UUID.randomUUID()).param("attempt", attempt).param("question", question)
                .param("correct", !"0".equals(awarded)).param("awarded", new BigDecimal(awarded)).update();
    }
}
