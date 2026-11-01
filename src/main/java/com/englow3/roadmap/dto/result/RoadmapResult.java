package com.englow3.roadmap.dto.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A learner's roadmap as the roadmap page shows it: the goal, the stages in force with their content and milestone, and
 * why the plan last changed.
 *
 * @param adjusting
 *            an adjustment is being worked out; the stages shown are still in force meanwhile
 * @param schedule
 *            ON_TRACK, or BEHIND when the current stage ran past its planned end
 */
public record RoadmapResult(UUID id, String status, String goalType, String targetCertificate, BigDecimal targetScore,
        String targetLevel, LocalDate targetDate, String startLevel, BigDecimal startScore, Integer dailyMinutes,
        boolean adjusting, String pendingReason, String failureCode, String schedule, Instant createdAt,
        Instant completedAt, List<Stage> stages, List<Adjustment> adjustments) {

    /**
     * @param percentDone
     *            mean progress across the stage's required items
     */
    public record Stage(UUID id, int orderNo, String title, String summary, String fromLevel, String toLevel,
            List<String> focusSkills, LocalDate plannedStart, LocalDate plannedEnd, String status, int percentDone,
            List<Item> items, Milestone milestone) {
    }

    public record Item(UUID id, String kind, UUID contentId, String title, boolean required, int targetPercent,
            int plannedWeek, int estimatedMinutes, int percentDone, boolean done) {
    }

    /**
     * @param ready
     *            every required item of the stage is done, so the milestone is the next thing to do
     */
    public record Milestone(UUID id, String kind, UUID examId, BigDecimal requiredScore, String requiredLevel,
            String status, BigDecimal resultScore, String resultLevel, int attemptCount, Instant achievedAt,
            boolean ready) {
    }

    public record Adjustment(String reason, Instant createdAt) {
    }
}
