package com.englow3.learning;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.learning.entity.QuizAttempt;
import com.englow3.learning.repository.QuizAttemptRepository;
import com.englow3.learning.repository.QuizRepository;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

/**
 * What the losing half of a double "Start" actually throws. The controllers retry a start once on
 * DataIntegrityViolationException and are handed the attempt the winner opened; if the unique index surfaced as some
 * other exception at commit, that retry would never run and the learner would see a conflict instead.
 */
class AttemptRaceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private QuizRepository quizzes;

    @Autowired
    private QuizAttemptRepository attempts;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void aSecondOpenAttemptFailsAsADataIntegrityViolation() {
        LearnerFixture fixture = new LearnerFixture(jdbc);
        UUID learner = fixture.learner();
        UUID quizId = fixture.publishedQuiz("Race", learner);
        fixture.quizAttempt(learner, quizId, null, false, null);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> attempts.save(
                QuizAttempt.start(quizzes.findById(quizId).orElseThrow(), learner, 1, BigDecimal.ONE, Instant.now()))))
                        .isInstanceOf(DataIntegrityViolationException.class);
    }
}
