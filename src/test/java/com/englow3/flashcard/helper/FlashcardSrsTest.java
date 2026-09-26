package com.englow3.flashcard.helper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.flashcard.entity.FlashcardReview;
import com.englow3.flashcard.entity.FlashcardReview.FlashcardSchedule;
import com.englow3.flashcard.entity.FlashcardReviewStatus;
import com.englow3.flashcard.entity.ReviewRating;

/**
 * The scheduler is the one part of flashcards with arithmetic worth getting wrong, and it is a pure function, so it is
 * tested directly rather than through the service.
 */
class FlashcardSrsTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    private FlashcardReview unseen() {
        return FlashcardReview.unseen(UUID.randomUUID(), UUID.randomUUID(), NOW);
    }

    /** Walks a card forward by answering it, so a test can describe a card that is several reviews old. */
    private FlashcardReview after(ReviewRating... ratings) {
        FlashcardReview review = unseen();
        for (ReviewRating rating : ratings) {
            review.applySchedule(rating, FlashcardSrs.schedule(review, rating, NOW), NOW);
        }
        return review;
    }

    @Nested
    class Intervals {

        @Test
        void firstSuccessComesBackTomorrow() {
            FlashcardSchedule schedule = FlashcardSrs.schedule(unseen(), ReviewRating.GOOD, NOW);

            assertThat(schedule.intervalDays()).isEqualTo(1);
            assertThat(schedule.dueAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
            assertThat(schedule.repetitions()).isEqualTo((short) 1);
        }

        @Test
        void secondSuccessComesBackInSixDays() {
            FlashcardSchedule schedule = FlashcardSrs.schedule(after(ReviewRating.GOOD), ReviewRating.GOOD, NOW);

            assertThat(schedule.intervalDays()).isEqualTo(6);
            assertThat(schedule.repetitions()).isEqualTo((short) 2);
        }

        /** From the third success the interval compounds by ease: 6 days at the default 2.50 is 15. */
        @Test
        void thirdSuccessMultipliesTheIntervalByEase() {
            FlashcardReview review = after(ReviewRating.GOOD, ReviewRating.GOOD);

            FlashcardSchedule schedule = FlashcardSrs.schedule(review, ReviewRating.GOOD, NOW);

            assertThat(review.getEaseFactor()).isEqualByComparingTo(FlashcardReview.INITIAL_EASE_FACTOR);
            assertThat(schedule.intervalDays()).isEqualTo(15);
        }

        /**
         * A failed card returns inside the same sitting, not tomorrow. Saying "again" and being told to come back in a
         * day is the opposite of what the button means.
         */
        @Test
        void failureReturnsWithinTheSameSession() {
            FlashcardSchedule schedule = FlashcardSrs.schedule(after(ReviewRating.GOOD, ReviewRating.GOOD),
                    ReviewRating.AGAIN, NOW);

            assertThat(schedule.intervalDays()).isZero();
            assertThat(schedule.dueAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
            assertThat(schedule.repetitions()).isZero();
        }
    }

    @Nested
    class EaseFactor {

        @Test
        void goodLeavesEaseAlone() {
            FlashcardSchedule schedule = FlashcardSrs.schedule(unseen(), ReviewRating.GOOD, NOW);

            assertThat(schedule.easeFactor()).isEqualByComparingTo(FlashcardReview.INITIAL_EASE_FACTOR);
        }

        @Test
        void easyRaisesEaseAndHardLowersIt() {
            assertThat(FlashcardSrs.schedule(unseen(), ReviewRating.EASY, NOW).easeFactor())
                    .isGreaterThan(FlashcardReview.INITIAL_EASE_FACTOR);
            assertThat(FlashcardSrs.schedule(unseen(), ReviewRating.HARD, NOW).easeFactor())
                    .isLessThan(FlashcardReview.INITIAL_EASE_FACTOR);
        }

        /**
         * Without a floor, a card the learner keeps failing drives ease toward zero and its interval rounds to nothing
         * - it would never leave the session.
         */
        @Test
        void easeNeverFallsBelowTheFloor() {
            FlashcardReview review = unseen();
            for (int i = 0; i < 20; i++) {
                review.applySchedule(ReviewRating.AGAIN, FlashcardSrs.schedule(review, ReviewRating.AGAIN, NOW), NOW);
            }

            assertThat(review.getEaseFactor()).isGreaterThanOrEqualTo(new BigDecimal("1.30"));
        }
    }

    @Nested
    class Status {

        @Test
        void aCardBeingLearnedIsNotYetUnderReview() {
            assertThat(FlashcardSrs.schedule(unseen(), ReviewRating.GOOD, NOW).status())
                    .isEqualTo(FlashcardReviewStatus.LEARNING);
        }

        @Test
        void twoSuccessesPutACardUnderReview() {
            assertThat(FlashcardSrs.schedule(after(ReviewRating.GOOD), ReviewRating.GOOD, NOW).status())
                    .isEqualTo(FlashcardReviewStatus.REVIEW);
        }

        @Test
        void fourSuccessesMasterIt() {
            FlashcardReview review = after(ReviewRating.GOOD, ReviewRating.GOOD, ReviewRating.GOOD);

            assertThat(FlashcardSrs.schedule(review, ReviewRating.GOOD, NOW).status())
                    .isEqualTo(FlashcardReviewStatus.MASTERED);
        }

        @Test
        void failingSendsACardBackToLearning() {
            FlashcardReview mastered = after(ReviewRating.GOOD, ReviewRating.GOOD, ReviewRating.GOOD,
                    ReviewRating.GOOD);
            assertThat(mastered.getStatus()).isEqualTo(FlashcardReviewStatus.MASTERED);

            assertThat(FlashcardSrs.schedule(mastered, ReviewRating.AGAIN, NOW).status())
                    .isEqualTo(FlashcardReviewStatus.LEARNING);
        }
    }

    @Nested
    class Lapses {

        /** Only a card that had been learned can relapse; failing one still being learned is ordinary progress. */
        @Test
        void failingACardUnderReviewCountsAsALapse() {
            FlashcardReview review = after(ReviewRating.GOOD, ReviewRating.GOOD);
            assertThat(review.getStatus()).isEqualTo(FlashcardReviewStatus.REVIEW);

            review.applySchedule(ReviewRating.AGAIN, FlashcardSrs.schedule(review, ReviewRating.AGAIN, NOW), NOW);

            assertThat(review.getLapseCount()).isEqualTo(1);
        }

        @Test
        void failingACardStillBeingLearnedDoesNot() {
            FlashcardReview review = after(ReviewRating.GOOD);
            assertThat(review.getStatus()).isEqualTo(FlashcardReviewStatus.LEARNING);

            review.applySchedule(ReviewRating.AGAIN, FlashcardSrs.schedule(review, ReviewRating.AGAIN, NOW), NOW);

            assertThat(review.getLapseCount()).isZero();
        }
    }
}
