package com.englow3.learning.service;

import java.util.ArrayList;
import java.util.List;

import com.englow3.learning.entity.DailyQuestKind;

/**
 * Today's goals, with real progress against them. Pure.
 * <p>
 * The targets are product rules - "ten sentences is a session" - and are stated here as constants. The progress is
 * always a count of rows, so a bar that reads 3/10 means the learner did three of something today and not that a
 * counter somewhere was nudged.
 * <p>
 * Quests carry no bonus reward. Points come from the work itself in {@link ExperiencePoints}, and a badge promising
 * extra points for the same work would either double-count it or be decoration pretending to be a payout.
 */
public final class DailyQuests {

    /** A session's worth of transcription. */
    static final int DAILY_SENTENCE_GOAL = 10;

    /** Length of the window the practise-every-day goal looks at. */
    public static final int WEEK_DAYS = 7;

    private DailyQuests() {
    }

    /**
     * @param cardsDueNow
     *            cards still due at the moment of asking, which is what makes the review goal move as they work
     * @param studyDaysThisWeek
     *            distinct days with any activity in the last {@value #WEEK_DAYS} days
     */
    public record TodayActivity(long cardsReviewedToday, long cardsDueNow, long quizzesPassedToday,
            long sentencesTypedToday, long studyDaysThisWeek) {
    }

    /** Progress is clamped to the target: a bar that reads 25/10 is a bar nobody designed. */
    public record Quest(DailyQuestKind kind, long progress, long target) {

        public static Quest of(DailyQuestKind kind, long progress, long target) {
            return new Quest(kind, Math.min(Math.max(progress, 0), target), target);
        }

        public boolean completed() {
            return progress >= target;
        }
    }

    public static List<Quest> forToday(TodayActivity activity) {
        List<Quest> quests = new ArrayList<>();

        // The target is what they had to clear today, which is what they have cleared plus what is left. It rises if
        // the schedule makes more cards due mid-session, and that is honest: the work did grow.
        long reviewTarget = activity.cardsReviewedToday() + activity.cardsDueNow();
        if (reviewTarget > 0) {
            quests.add(Quest.of(DailyQuestKind.REVIEW_DUE_CARDS, activity.cardsReviewedToday(), reviewTarget));
        }

        quests.add(Quest.of(DailyQuestKind.PASS_A_QUIZ, activity.quizzesPassedToday(), 1));
        quests.add(Quest.of(DailyQuestKind.TYPE_SENTENCES, activity.sentencesTypedToday(), DAILY_SENTENCE_GOAL));
        quests.add(Quest.of(DailyQuestKind.PRACTISE_EVERY_DAY, activity.studyDaysThisWeek(), WEEK_DAYS));

        return quests;
    }
}
