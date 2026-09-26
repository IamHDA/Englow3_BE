package com.englow3.learning.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

import com.englow3.learning.entity.FlashcardReview;
import com.englow3.learning.entity.FlashcardReview.FlashcardSchedule;
import com.englow3.learning.entity.FlashcardReviewStatus;
import com.englow3.learning.entity.ReviewRating;

/**
 * SM-2, as the interface's four buttons rather than the paper's 0-5 grades. A pure function of (current state, rating,
 * now): no repository, no clock of its own, nothing to mock. That is the point - scheduling is the one piece of this
 * feature with arithmetic worth getting wrong, so it is the one piece that can be tested exhaustively without a
 * database.
 */
public final class FlashcardSrs {

    /**
     * SM-2's floor. Without it a card the learner keeps failing drives its ease toward zero and comes back on an
     * interval that rounds to nothing, so it never leaves the session.
     */
    private static final BigDecimal MIN_EASE_FACTOR = new BigDecimal("1.30");

    /** Quality grades, in SM-2's 0-5 scale, that each button stands for. */
    private static final int QUALITY_AGAIN = 2;
    private static final int QUALITY_HARD = 3;
    private static final int QUALITY_GOOD = 4;
    private static final int QUALITY_EASY = 5;

    /** The first two successful intervals are fixed by SM-2; the ease factor only starts to matter from the third. */
    private static final int FIRST_INTERVAL_DAYS = 1;
    private static final int SECOND_INTERVAL_DAYS = 6;

    /**
     * A failed card is not pushed a day out - it comes back inside the same session, which is the whole point of saying
     * "again". Ten minutes is short enough to be the same sitting and long enough for a few other cards.
     */
    private static final Duration RELEARN_DELAY = Duration.ofMinutes(10);

    /**
     * Repetitions at which a card is called mastered. Not an SM-2 concept - it is a label for the interface, chosen so
     * that a card only earns it after surviving the 1-day and 6-day intervals and one ease-driven one beyond them.
     */
    private static final int MASTERED_REPETITIONS = 4;

    /**
     * A hundred years, as Anki caps it. Compounding by ease has no ceiling of its own: some twenty successes in a row
     * would push the due date past the largest timestamp Postgres stores, and every later answer on that card would
     * fail.
     */
    static final int MAX_INTERVAL_DAYS = 36_500;

    private FlashcardSrs() {
    }

    public static FlashcardSchedule schedule(FlashcardReview review, ReviewRating rating, Instant now) {
        BigDecimal easeFactor = nextEaseFactor(review.getEaseFactor(), rating);

        if (rating.isFailure()) {
            // Repetitions reset, ease keeps its penalty: the learner has to climb the ladder again, but the card stays
            // marked as one they find hard.
            return new FlashcardSchedule((short) 0, easeFactor, 0, now.plus(RELEARN_DELAY),
                    FlashcardReviewStatus.LEARNING);
        }

        short repetitions = (short) (review.getRepetitions() + 1);
        int intervalDays = nextIntervalDays(review.getIntervalDays(), repetitions, easeFactor);

        return new FlashcardSchedule(repetitions, easeFactor, intervalDays, now.plus(Duration.ofDays(intervalDays)),
                statusFor(repetitions));
    }

    private static int nextIntervalDays(int currentIntervalDays, short repetitions, BigDecimal easeFactor) {
        if (repetitions == 1) {
            return FIRST_INTERVAL_DAYS;
        }
        if (repetitions == 2) {
            return SECOND_INTERVAL_DAYS;
        }
        // From here the interval compounds by ease. Rounded up so it always moves: rounding down would let a low-ease
        // card repeat the same interval forever.
        BigDecimal next = BigDecimal.valueOf(currentIntervalDays).multiply(easeFactor).setScale(0,
                RoundingMode.CEILING);
        return next.min(BigDecimal.valueOf(MAX_INTERVAL_DAYS)).intValueExact();
    }

    /**
     * SM-2's ease update, {@code EF' = EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02))}. GOOD leaves the ease alone, EASY
     * raises it, HARD and AGAIN lower it by different amounts.
     */
    private static BigDecimal nextEaseFactor(BigDecimal current, ReviewRating rating) {
        int quality = qualityOf(rating);
        BigDecimal gap = BigDecimal.valueOf(5 - quality);
        BigDecimal delta = new BigDecimal("0.10")
                .subtract(gap.multiply(new BigDecimal("0.08").add(gap.multiply(new BigDecimal("0.02")))));

        BigDecimal next = current.add(delta).setScale(2, RoundingMode.HALF_UP);
        return next.max(MIN_EASE_FACTOR);
    }

    private static int qualityOf(ReviewRating rating) {
        return switch (rating) {
            case AGAIN -> QUALITY_AGAIN;
            case HARD -> QUALITY_HARD;
            case GOOD -> QUALITY_GOOD;
            case EASY -> QUALITY_EASY;
        };
    }

    private static FlashcardReviewStatus statusFor(short repetitions) {
        if (repetitions >= MASTERED_REPETITIONS) {
            return FlashcardReviewStatus.MASTERED;
        }
        // Two successes are what SM-2 needs before an interval is driven by ease rather than fixed; that is the line
        // between still learning a card and genuinely reviewing it.
        return repetitions >= 2 ? FlashcardReviewStatus.REVIEW : FlashcardReviewStatus.LEARNING;
    }
}
