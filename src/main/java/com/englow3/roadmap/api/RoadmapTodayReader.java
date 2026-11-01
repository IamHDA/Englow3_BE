package com.englow3.roadmap.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What the learner's roadmap asks of them now, for the daily path. Plain values only; the roadmap's entities and enums
 * stay inside it.
 */
public interface RoadmapTodayReader {

    /** Empty when the learner has no active roadmap - the daily path then falls back to its own rules. */
    Optional<TodayPlan> today(UUID userId);

    /**
     * @param items
     *            unfinished content of the current stage due by this week of it, required items first, in plan order
     * @param milestone
     *            the stage's closing test once every required item is done; null until then
     */
    record TodayPlan(UUID roadmapId, String stageTitle, int stageOrder, List<TodayItem> items,
            TodayMilestone milestone) {
    }

    /**
     * @param kind
     *            FLASHCARD_SET, DICTATION_LESSON, QUIZ, SPEAKING_PROMPT or ASSESSMENT_TASK
     * @param skill
     *            what it trains; for a writing/speaking task, WRITING or SPEAKING
     */
    record TodayItem(String kind, UUID contentId, String title, String skill, int percentDone, int targetPercent) {
    }

    /**
     * @param kind
     *            CERTIFICATE_MOCK (sit {@code examId}) or LEVEL_CHECK (start a level check for {@code milestoneId})
     */
    record TodayMilestone(UUID milestoneId, String kind, UUID examId) {
    }
}
