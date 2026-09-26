package com.englow3.quiz.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

class QuizReviewLifecycleTest {

    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();

    @Test
    void sendsADraftToTheQueue() {
        Quiz quiz = draftQuiz();

        quiz.submitForReview(10, 100, Instant.now());

        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.PENDING_REVIEW);
    }

    /** A quiz whose questions are all worth nothing cannot be scored, so it cannot be sent on either. */
    @Test
    void refusesToSubmitAQuizWorthNoPoints() {
        assertThatThrownBy(() -> draftQuiz().submitForReview(10, 0, Instant.now()))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("QUIZ_ZERO_POINTS");
    }

    @Test
    void publishesOnApproval() {
        Quiz quiz = pendingQuiz();
        Instant approvedAt = Instant.now();

        quiz.approve(REVIEWER_ID, 10, 100, approvedAt);

        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.PUBLISHED);
        assertThat(quiz.getPublishedAt()).isEqualTo(approvedAt);
    }

    /** Questions live in another table, so approval re-checks rather than trusting submission time. */
    @Test
    void refusesToApproveAQuizThatHasSinceLostItsQuestions() {
        assertThatThrownBy(() -> pendingQuiz().approve(REVIEWER_ID, 0, 0, Instant.now()))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("QUIZ_EMPTY");
    }

    @Test
    void dropsAStaleNoteOnApproval() {
        Quiz quiz = pendingQuiz();
        quiz.reject(REVIEWER_ID, "Question 4 has two right answers.", Instant.now());
        quiz.submitForReview(10, 100, Instant.now());

        quiz.approve(REVIEWER_ID, 10, 100, Instant.now());

        assertThat(quiz.getReview().getReviewNote()).isNull();
    }

    private static Quiz draftQuiz() {
        return Quiz.draft("tenses", "Tenses", "", "grammar", "B1", 600, (short) 70, AUTHOR_ID);
    }

    private static Quiz pendingQuiz() {
        Quiz quiz = draftQuiz();
        quiz.submitForReview(10, 100, Instant.now());
        return quiz;
    }

    @Test
    void refusesARejectionWithNoReasonWithoutChangingStatus() {
        Quiz quiz = pendingQuiz();

        assertThatThrownBy(() -> quiz.reject(REVIEWER_ID, null, Instant.now())).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("REVIEW_NOTE_REQUIRED");
        assertThat(quiz.getStatus()).isEqualTo(QuizStatus.PENDING_REVIEW);
    }
}
