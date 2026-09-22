package com.englow3.learning.service;

/**
 * The experience counter and level shown on the daily path.
 * <p>
 * Derived from activity rather than stored in a ledger, and that is the point: there is no table to drift, no writer to
 * forget an increment, and no way for the counter to disagree with the work it is counting. Recomputing it is four
 * counts.
 * <p>
 * Points are awarded for work done, never for being right. A learner who answers ten cards wrong did ten cards of work,
 * and paying out for correctness would turn this into a second, worse score - the real scores already live on the quiz
 * and exam results. The weights are proportional to effort, not to merit.
 */
public final class ExperiencePoints {

    /** One card rated in a review session. */
    public static final int FLASHCARD_REVIEW_XP = 2;

    /** One dictation sentence transcribed - typing a whole line is more work than rating a card. */
    public static final int DICTATION_SENTENCE_XP = 5;

    /** One quiz submitted, whatever the score. */
    public static final int QUIZ_ATTEMPT_XP = 20;

    /** One exam attempt scored. A full paper is an hour or more, so it is worth a session of anything else. */
    public static final int EXAM_ATTEMPT_XP = 100;

    /**
     * What the first level costs, and how much more each level costs than the one before. Linear growth rather than
     * exponential: the point is to keep the next level in sight, and a curve that doubles puts level 10 out of reach of
     * anyone who is not already studying daily.
     */
    static final int FIRST_LEVEL_COST_XP = 200;
    static final int LEVEL_COST_GROWTH_XP = 100;

    private ExperiencePoints() {
    }

    /** Units of work done, as counted from the activity tables. */
    public record Activity(long flashcardReviews, long dictationSentences, long quizAttempts, long examAttempts) {

        public static final Activity NONE = new Activity(0, 0, 0, 0);
    }

    /**
     * @param level
     *            starts at 1, so a learner who has done nothing is level 1 rather than level 0
     * @param xpIntoLevel
     *            points earned since reaching this level
     * @param levelCostXp
     *            points needed to leave it
     */
    public record Level(int level, long totalXp, long xpIntoLevel, long levelCostXp) {
    }

    public static long totalXp(Activity activity) {
        return activity.flashcardReviews() * FLASHCARD_REVIEW_XP + activity.dictationSentences() * DICTATION_SENTENCE_XP
                + activity.quizAttempts() * QUIZ_ATTEMPT_XP + activity.examAttempts() * EXAM_ATTEMPT_XP;
    }

    /** Walks the curve rather than inverting it: the arithmetic is obvious at a glance and levels are small numbers. */
    public static Level levelFor(long totalXp) {
        int level = 1;
        long remaining = Math.max(totalXp, 0);
        long cost = FIRST_LEVEL_COST_XP;

        while (remaining >= cost) {
            remaining -= cost;
            level++;
            cost += LEVEL_COST_GROWTH_XP;
        }

        return new Level(level, Math.max(totalXp, 0), remaining, cost);
    }
}
