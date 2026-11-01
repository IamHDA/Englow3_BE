package com.englow3.roadmap.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.englow3.roadmap.dto.result.RoadmapResult;
import com.englow3.roadmap.entity.AdjustmentReason;
import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.entity.MilestoneKind;
import com.englow3.roadmap.entity.MilestoneStatus;
import com.englow3.roadmap.entity.RoadmapContentKind;
import com.englow3.roadmap.entity.RoadmapGoalType;
import com.englow3.roadmap.entity.RoadmapSchedule;
import com.englow3.roadmap.entity.RoadmapStageStatus;
import com.englow3.roadmap.entity.RoadmapStatus;

/** The learner's roadmap: goal, stages in force with their content and milestone, and the latest changes. */
public record RoadmapResponse(UUID id, RoadmapStatus status, RoadmapGoalType goalType, String targetCertificate,
        BigDecimal targetScore, CefrLevel targetLevel, LocalDate targetDate, CefrLevel startLevel,
        BigDecimal startScore, Integer dailyMinutes, boolean adjusting, AdjustmentReason pendingReason,
        String failureCode, RoadmapSchedule schedule, Instant createdAt, Instant completedAt,
        List<StageResponse> stages, List<AdjustmentResponse> adjustments) {

    public static RoadmapResponse from(RoadmapResult r) {
        return new RoadmapResponse(r.id(), r.status(), r.goalType(), r.targetCertificate(), r.targetScore(),
                r.targetLevel(), r.targetDate(), r.startLevel(), r.startScore(), r.dailyMinutes(), r.adjusting(),
                r.pendingReason(), r.failureCode(), r.schedule(), r.createdAt(), r.completedAt(),
                r.stages().stream().map(StageResponse::from).toList(),
                r.adjustments().stream().map(a -> new AdjustmentResponse(a.reason(), a.createdAt())).toList());
    }

    public record StageResponse(UUID id, int orderNo, String title, String summary, CefrLevel fromLevel,
            CefrLevel toLevel, List<String> focusSkills, LocalDate plannedStart, LocalDate plannedEnd,
            RoadmapStageStatus status, int percentDone, List<ItemResponse> items, MilestoneResponse milestone) {

        static StageResponse from(RoadmapResult.Stage s) {
            RoadmapResult.Milestone m = s.milestone();
            return new StageResponse(s.id(), s.orderNo(), s.title(), s.summary(), s.fromLevel(), s.toLevel(),
                    s.focusSkills(), s.plannedStart(), s.plannedEnd(), s.status(), s.percentDone(),
                    s.items().stream()
                            .map(i -> new ItemResponse(i.id(), i.kind(), i.contentId(), i.title(), i.skill(),
                                    i.required(), i.targetPercent(), i.plannedWeek(), i.estimatedMinutes(),
                                    i.percentDone(), i.done()))
                            .toList(),
                    m == null ? null
                            : new MilestoneResponse(m.id(), m.kind(), m.examId(), m.requiredScore(), m.requiredLevel(),
                                    m.status(), m.resultScore(), m.resultLevel(), m.attemptCount(), m.achievedAt(),
                                    m.ready()));
        }
    }

    /**
     * @param contentId
     *            the set, lesson, quiz, prompt or task to open - the client links it by kind
     */
    public record ItemResponse(UUID id, RoadmapContentKind kind, UUID contentId, String title, String skill,
            boolean required, int targetPercent, int plannedWeek, int estimatedMinutes, int percentDone, boolean done) {
    }

    /**
     * @param ready
     *            every required item of the stage is done
     */
    public record MilestoneResponse(UUID id, MilestoneKind kind, UUID examId, BigDecimal requiredScore,
            CefrLevel requiredLevel, MilestoneStatus status, BigDecimal resultScore, CefrLevel resultLevel,
            int attemptCount, Instant achievedAt, boolean ready) {
    }

    public record AdjustmentResponse(AdjustmentReason reason, Instant createdAt) {
    }
}
