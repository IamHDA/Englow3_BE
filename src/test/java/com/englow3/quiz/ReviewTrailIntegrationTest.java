package com.englow3.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import com.englow3.quiz.dto.result.ContentReviewResult;
import com.englow3.quiz.repository.QuizRepository;
import com.englow3.support.LearnerFixture;
import com.englow3.support.PostgresIntegrationTest;

class ReviewTrailIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private QuizRepository quizzes;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void listsADraftThatNobodyHasReviewed() {
        LearnerFixture fixture = new LearnerFixture(jdbc);
        UUID authorId = fixture.learner();
        UUID quizId = UUID.randomUUID();
        jdbc.sql("""
                insert into quizzes (id, slug, title, description, category, time_limit_seconds,
                                     passing_score_percent, status, created_by_user_id)
                values (:id, :slug, 'Never reviewed', '', 'grammar', 600, 70, 'DRAFT', :authorId)
                """).param("id", quizId).param("slug", "quiz-" + quizId).param("authorId", authorId).update();

        transactions.executeWithoutResult(status -> {
            var quiz = quizzes.findById(quizId).orElseThrow();

            assertThat(quiz.getReview()).isNotNull();
            assertThatCode(() -> ContentReviewResult.of(quiz, 0)).doesNotThrowAnyException();
        });
    }
}
