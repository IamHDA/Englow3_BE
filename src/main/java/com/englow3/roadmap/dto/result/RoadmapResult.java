package com.englow3.roadmap.dto.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.englow3.roadmap.entity.AdjustmentReason;
import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.entity.MilestoneKind;
import com.englow3.roadmap.entity.MilestoneStatus;
import com.englow3.roadmap.entity.RoadmapContentKind;
import com.englow3.roadmap.entity.RoadmapGoalType;
import com.englow3.roadmap.entity.RoadmapSchedule;
import com.englow3.roadmap.entity.RoadmapStageStatus;
import com.englow3.roadmap.entity.RoadmapStatus;

/**
 * A learner's roadmap as the roadmap page shows it: the goal, the stages in force with their content and milestone, and
 * why the plan last changed.
 *
 * @param adjusting
 *            an adjustment is being worked out; the stages shown are still in force meanwhile
 */
public record RoadmapResult(UUID id, RoadmapStatus status, RoadmapGoalType goalType, String targetCertificate,
        BigDecimal targetScore, CefrLevel targetLevel, LocalDate targetDate, CefrLevel startLevel,
        BigDecimal startScore, Integer dailyMinutes, boolean adjusting, AdjustmentReason pendingReason,
        String failureCode, RoadmapSchedule schedule, Instant createdAt, Instant completedAt, List<Stage> stages,
        List<Adjustment> adjustments) {

    /**
     * @param percentDone
     *            mean progress across the stage's required items
     */
    public record Stage(UUID id, int orderNo, String title, String summary, CefrLevel fromLevel, CefrLevel toLevel,
            List<String> focusSkills, LocalDate plannedStart, LocalDate plannedEnd, RoadmapStageStatus status,
            int percentDone, List<Item> items, Milestone milestone) {
    }

    public record Item(UUID id, RoadmapContentKind kind, UUID contentId, String title, String skill, boolean required,
            int targetPercent, int plannedWeek, int estimatedMinutes, int percentDone, boolean done) {
    }

    /**
     * @param ready
     *            every required item of the stage is done, so the milestone is the next thing to do
     */
    /**
     * @param fullTestId
     *            set instead of {@code examId} on an IELTS mock milestone
     */
    public record Milestone(UUID id, MilestoneKind kind, UUID examId, BigDecimal requiredScore, CefrLevel requiredLevel,
            MilestoneStatus status, BigDecimal resultScore, CefrLevel resultLevel, int attemptCount, Instant achievedAt,
            boolean ready, UUID fullTestId) {
    }

    public record Adjustment(AdjustmentReason reason, Instant createdAt) {
    }
}
