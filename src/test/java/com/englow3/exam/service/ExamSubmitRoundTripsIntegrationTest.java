package com.englow3.exam.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

import com.englow3.exam.dto.command.StartExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand;
import com.englow3.exam.dto.command.SubmitExamAttemptCommand.SubmittedAnswer;
import com.englow3.exam.dto.result.ExamAttemptResult;
import com.englow3.exam.entity.ExamAttemptStatus;
import com.englow3.support.ExamFixture;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;
import com.englow3.support.SignedIn;

import jakarta.persistence.EntityManagerFactory;

/**
 * How many statements a submit costs. A full test of 200 questions took half a minute to submit on a remote database,
 * because every answer was read before it was inserted and then inserted alone: hundreds of round trips, each a network
 * wait. The count below is what stays flat however long the paper is.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ExamSubmitRoundTripsIntegrationTest extends PostgresIntegrationTest {

    private static final int QUESTIONS = 80;

    @Autowired
    private ExamAttemptService attempts;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcClient jdbc;

    private UUID examId;
    private final List<UUID[]> questions = new ArrayList<>();

    @BeforeEach
    void aLongPaper() {
        LearnerFixture learners = new LearnerFixture(jdbc);
        UUID author = learners.learner();
        UUID learner = learners.learner();
        ExamFixture exams = new ExamFixture(jdbc);
        examId = exams.publishedExam("Round trips", author);
        UUID part = exams.part(exams.section(examId, 1, "READING"), 1, "Part 5");
        UUID set = exams.questionSet(part, 1);
        for (int number = 1; number <= QUESTIONS; number++) {
            UUID question = exams.question(set, number, "Question " + number);
            UUID right = exams.option(question, 1, "right", true);
            exams.option(question, 2, "wrong", false);
            questions.add(new UUID[] { question, right });
        }
        SignedIn.as(jdbc, learner);
    }

    @AfterEach
    void signOut() {
        SignedIn.out();
    }

    @Test
    void submittingALongPaperDoesNotCostAStatementPerAnswer() {
        ExamAttemptResult sitting = attempts.start(StartExamAttemptCommand.full(examId));
        List<SubmittedAnswer> answers = questions.stream().map(q -> new SubmittedAnswer(q[0], List.of(q[1]))).toList();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        ExamAttemptResult scored = attempts.submit(new SubmitExamAttemptCommand(sitting.id(), answers));

        assertThat(scored.status()).isEqualTo(ExamAttemptStatus.SCORED);
        assertThat(scored.correctAnswerCount()).isEqualTo(QUESTIONS);
        // Read per row and inserted per row would be 3 x QUESTIONS (240) or more. Batched it is a few dozen at most,
        // and the same few dozen for a paper twice the length.
        assertThat(statistics.getPrepareStatementCount()).isLessThan(QUESTIONS);
    }
}
