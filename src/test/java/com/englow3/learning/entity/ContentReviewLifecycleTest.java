package com.englow3.learning.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

/**
 * This module's three content types run the same review workflow through three separate status enums. Tested together
 * so the three stay in step: if one of them ever grows a rule the others do not have, this file is where it shows.
 * <p>
 * A fourth type runs the same workflow outside this module - see {@code speaking.entity.SpeakingPromptTest}. It is
 * tested there rather than here because a learning-module test importing a speaking entity would cross the boundary the
 * module map exists to hold.
 */
class ContentReviewLifecycleTest {

    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();

    @Nested
    class Sets {

        @Test
        void sendsADraftToTheQueue() {
            FlashcardSet set = draftSet();
            Instant submittedAt = Instant.now();

            set.submitForReview(20, submittedAt);

            assertThat(set.getStatus()).isEqualTo(FlashcardSetStatus.PENDING_REVIEW);
            assertThat(set.getReview().getSubmittedForReviewAt()).isEqualTo(submittedAt);
            assertThat(set.getPublishedAt()).isNull();
        }

        /** A reviewer opening an empty set learns nothing they can act on, and the author is who can fill it. */
        @Test
        void refusesToSubmitAnEmptySet() {
            assertThatThrownBy(() -> draftSet().submitForReview(0, Instant.now())).isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("FLASHCARD_SET_EMPTY");
        }

        @Test
        void publishesOnApprovalAndRecordsWhoDecided() {
            FlashcardSet set = pendingSet();
            Instant approvedAt = Instant.now();

            set.approve(REVIEWER_ID, 20, approvedAt);

            assertThat(set.getStatus()).isEqualTo(FlashcardSetStatus.PUBLISHED);
            assertThat(set.getPublishedAt()).isEqualTo(approvedAt);
            assertThat(set.getReview().getReviewedByUserId()).isEqualTo(REVIEWER_ID);
        }

        @Test
        void refusesToApproveASetNobodySubmitted() {
            assertThatThrownBy(() -> draftSet().approve(REVIEWER_ID, 20, Instant.now()))
                    .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                    .isEqualTo("FLASHCARD_SET_NOT_PENDING_REVIEW");
        }

        @Test
        void keepsTheReasonWhenTurningASetBack() {
            FlashcardSet set = pendingSet();

            set.reject(REVIEWER_ID, "  Twelve cards have no audio.  ", Instant.now());

            assertThat(set.getStatus()).isEqualTo(FlashcardSetStatus.REJECTED);
            assertThat(set.getReview().getReviewNote()).isEqualTo("Twelve cards have no audio.");
        }

        @Test
        void letsARejectedSetBeResubmitted() {
            FlashcardSet set = pendingSet();
            set.reject(REVIEWER_ID, "Twelve cards have no audio.", Instant.now());

            set.submitForReview(20, Instant.now());

            assertThat(set.getStatus()).isEqualTo(FlashcardSetStatus.PENDING_REVIEW);
        }

        private static FlashcardSet draftSet() {
            return FlashcardSet.draft("core-500", "Core 500", "", "general", "B1", AUTHOR_ID);
        }

        private static FlashcardSet pendingSet() {
            FlashcardSet set = draftSet();
            set.submitForReview(20, Instant.now());
            return set;
        }
    }

    @Nested
    class Quizzes {

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
    }

    @Nested
    class Lessons {

        @Test
        void sendsADraftToTheQueue() {
            DictationLesson lesson = draftLesson();

            lesson.submitForReview(8, Instant.now());

            assertThat(lesson.getStatus()).isEqualTo(DictationLessonStatus.PENDING_REVIEW);
        }

        @Test
        void refusesToSubmitAnEmptyLesson() {
            assertThatThrownBy(() -> draftLesson().submitForReview(0, Instant.now()))
                    .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                    .isEqualTo("DICTATION_LESSON_EMPTY");
        }

        @Test
        void publishesOnApproval() {
            DictationLesson lesson = pendingLesson();

            lesson.approve(REVIEWER_ID, 8, Instant.now());

            assertThat(lesson.getStatus()).isEqualTo(DictationLessonStatus.PUBLISHED);
        }

        @Test
        void retiresALessonWaitingOnReview() {
            DictationLesson lesson = pendingLesson();

            lesson.archive();

            assertThat(lesson.getStatus()).isEqualTo(DictationLessonStatus.ARCHIVED);
        }

        private static DictationLesson draftLesson() {
            return DictationLesson.draft("airport", "At the airport", "travel", "A2", AUTHOR_ID);
        }

        private static DictationLesson pendingLesson() {
            DictationLesson lesson = draftLesson();
            lesson.submitForReview(8, Instant.now());
            return lesson;
        }
    }

    /** One code for all three types: the rule and the message are the same, so a client maps one thing. */
    @Nested
    class TheNote {

        @Test
        void refusesARejectionWithNoReasonOnEveryType() {
            FlashcardSet set = FlashcardSet.draft("core-500", "Core 500", "", "general", "B1", AUTHOR_ID);
            set.submitForReview(20, Instant.now());
            Quiz quiz = Quiz.draft("tenses", "Tenses", "", "grammar", "B1", 600, (short) 70, AUTHOR_ID);
            quiz.submitForReview(10, 100, Instant.now());
            DictationLesson lesson = DictationLesson.draft("airport", "At the airport", "travel", "A2", AUTHOR_ID);
            lesson.submitForReview(8, Instant.now());

            assertThatThrownBy(() -> set.reject(REVIEWER_ID, "  ", Instant.now()))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("REVIEW_NOTE_REQUIRED");
            assertThatThrownBy(() -> quiz.reject(REVIEWER_ID, null, Instant.now()))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("REVIEW_NOTE_REQUIRED");
            assertThatThrownBy(() -> lesson.reject(REVIEWER_ID, "", Instant.now()))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("REVIEW_NOTE_REQUIRED");
        }

        /**
         * A refused rejection must not half-apply. The note check runs before the status moves, so content stays in the
         * queue rather than landing in REJECTED with nothing to explain it.
         */
        @Test
        void leavesContentInTheQueueWhenTheReasonIsMissing() {
            Quiz quiz = Quiz.draft("tenses", "Tenses", "", "grammar", "B1", 600, (short) 70, AUTHOR_ID);
            quiz.submitForReview(10, 100, Instant.now());

            assertThatThrownBy(() -> quiz.reject(REVIEWER_ID, " ", Instant.now()))
                    .isInstanceOf(BadRequestException.class);

            assertThat(quiz.getStatus()).isEqualTo(QuizStatus.PENDING_REVIEW);
        }
    }
}
