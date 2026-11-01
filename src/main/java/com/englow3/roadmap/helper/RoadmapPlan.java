package com.englow3.roadmap.helper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.entity.RoadmapContentKind;
import com.englow3.roadmap.entity.RoadmapGoalType;

/** The values a roadmap is planned from and the plan the AI proposes, as plain records. */
public final class RoadmapPlan {

    private RoadmapPlan() {
    }

    /** A published piece of content the AI may schedule. It may choose only from these. */
    public record Candidate(RoadmapContentKind kind, UUID id, String title, CefrLevel level, String skill,
            int estimatedMinutes) {
    }

    /** A published mock paper of the learner's certificate, for a milestone. */
    public record MockPaper(UUID id, String title, String variant) {
    }

    /**
     * What a proposal is checked against.
     *
     * @param firstOrderNo
     *            the position of the first proposed stage: 1 for a new plan, after the passed stages for an adjustment
     * @param startLevel
     *            the level the first proposed stage starts from
     * @param minimumScore
     *            for a certificate goal, the lowest score a milestone may ask for (the last passed one, or zero)
     */
    public record Context(RoadmapGoalType goalType, CefrLevel startLevel, CefrLevel targetLevel,
            String targetCertificate, BigDecimal targetScore, BigDecimal minimumScore, LocalDate today,
            LocalDate targetDate, int dailyMinutes, int firstOrderNo, List<Candidate> candidates,
            List<MockPaper> mockPapers) {
    }

    public record ProposedItem(RoadmapContentKind kind, UUID contentId, String title, boolean required,
            int targetPercent, int plannedWeek, int estimatedMinutes) {
    }

    public record ProposedMilestone(UUID examId, BigDecimal requiredScore, CefrLevel requiredLevel) {
    }

    public record ProposedStage(String title, String summary, CefrLevel fromLevel, CefrLevel toLevel,
            List<String> focusSkills, LocalDate plannedStart, LocalDate plannedEnd, List<ProposedItem> items,
            ProposedMilestone milestone) {
    }
}
