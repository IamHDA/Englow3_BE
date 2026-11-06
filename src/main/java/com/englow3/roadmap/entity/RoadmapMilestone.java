package com.englow3.roadmap.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * The test that closes a stage: a mock paper at a score, or the level check at a band. A result is taken once per
 * sitting and only from a sitting that is finished and scored - the service establishes that the sitting is the
 * learner's own before it gets here.
 */
@Entity
@Table(name = "roadmap_milestones")
@Getter
public class RoadmapMilestone extends BasePersistedEntity {

    private UUID stageId;
    @Enumerated(EnumType.STRING)
    private MilestoneKind kind;
    private UUID examId;
    /**
     * Instead of {@code examId} on an IELTS mock milestone: the exam module's full test, passed on its overall band.
     */
    private UUID fullTestId;
    private BigDecimal requiredScore;
    @Enumerated(EnumType.STRING)
    private CefrLevel requiredLevel;
    private UUID resultExamAttemptId;
    private UUID resultFullTestSittingId;
    private UUID resultLevelCheckId;
    private BigDecimal resultScore;
    @Enumerated(EnumType.STRING)
    private CefrLevel resultLevel;
    private int attemptCount;
    @Enumerated(EnumType.STRING)
    private MilestoneStatus status;
    private Instant achievedAt;
    @UpdateTimestamp
    private Instant updatedAt;

    protected RoadmapMilestone() {
    }

    public static RoadmapMilestone mockExam(UUID id, UUID stageId, UUID examId, BigDecimal requiredScore) {
        if (examId == null || requiredScore == null || requiredScore.signum() <= 0) {
            throw new BadRequestException("ROADMAP_MILESTONE_INVALID", "A mock milestone needs a paper and a score");
        }
        RoadmapMilestone milestone = base(id, stageId, MilestoneKind.CERTIFICATE_MOCK);
        milestone.examId = examId;
        milestone.requiredScore = requiredScore;
        return milestone;
    }

    /** An IELTS mock milestone: a full test, passed when its overall band reaches {@code requiredScore}. */
    public static RoadmapMilestone mockFullTest(UUID id, UUID stageId, UUID fullTestId, BigDecimal requiredScore) {
        if (fullTestId == null || requiredScore == null || requiredScore.signum() <= 0) {
            throw new BadRequestException("ROADMAP_MILESTONE_INVALID",
                    "A mock milestone needs a full test and a score");
        }
        RoadmapMilestone milestone = base(id, stageId, MilestoneKind.CERTIFICATE_MOCK);
        milestone.fullTestId = fullTestId;
        milestone.requiredScore = requiredScore;
        return milestone;
    }

    public boolean isFullTest() {
        return fullTestId != null;
    }

    public static RoadmapMilestone levelCheck(UUID id, UUID stageId, CefrLevel requiredLevel) {
        if (requiredLevel == null) {
            throw new BadRequestException("ROADMAP_MILESTONE_INVALID", "A level milestone needs a band");
        }
        RoadmapMilestone milestone = base(id, stageId, MilestoneKind.LEVEL_CHECK);
        milestone.requiredLevel = requiredLevel;
        return milestone;
    }

    private static RoadmapMilestone base(UUID id, UUID stageId, MilestoneKind kind) {
        RoadmapMilestone milestone = new RoadmapMilestone();
        milestone.id = Objects.requireNonNull(id);
        milestone.stageId = Objects.requireNonNull(stageId);
        milestone.kind = kind;
        milestone.status = MilestoneStatus.PENDING;
        return milestone;
    }

    /**
     * A sitting of the mock paper.
     *
     * @param examId
     *            the paper the sitting was of
     * @param scored
     *            the sitting is finished and graded
     * @param full
     *            the whole paper rather than a practice of chosen parts
     */
    public void recordMockSitting(UUID attemptId, UUID examId, boolean scored, boolean full, BigDecimal score,
            Instant now) {
        requireKind(MilestoneKind.CERTIFICATE_MOCK);
        if (isFullTest()) {
            throw new BadRequestException("MILESTONE_NEEDS_FULL_TEST",
                    "This milestone is passed by a full test, not by a paper on its own");
        }
        requireOpenFor(attemptId.equals(resultExamAttemptId));
        if (!this.examId.equals(examId)) {
            throw new BadRequestException("MILESTONE_WRONG_EXAM", "That sitting is of a different paper");
        }
        if (!scored || !full || score == null) {
            throw new BadRequestException("MILESTONE_RESULT_NOT_SCORED",
                    "Only a finished, scored sitting of the whole paper counts");
        }
        resultExamAttemptId = attemptId;
        resultScore = score;
        settle(score.compareTo(requiredScore) >= 0, now);
    }

    /**
     * A completed sitting of the milestone's full test.
     *
     * @param overallBand
     *            null when the sitting has no overall band yet, which does not count
     */
    public void recordFullTestSitting(UUID sittingId, UUID fullTestId, BigDecimal overallBand, Instant now) {
        requireKind(MilestoneKind.CERTIFICATE_MOCK);
        if (!isFullTest()) {
            throw new BadRequestException("MILESTONE_WRONG_EXAM", "This milestone is passed by a paper");
        }
        requireOpenFor(sittingId.equals(resultFullTestSittingId));
        if (!this.fullTestId.equals(fullTestId)) {
            throw new BadRequestException("MILESTONE_WRONG_EXAM", "That sitting is of a different full test");
        }
        if (overallBand == null) {
            throw new BadRequestException("MILESTONE_RESULT_NOT_SCORED", "Only a completed full test counts");
        }
        resultFullTestSittingId = sittingId;
        resultScore = overallBand;
        settle(overallBand.compareTo(requiredScore) >= 0, now);
    }

    /** A completed level check. */
    public void recordLevelCheck(UUID levelCheckId, CefrLevel level, Instant now) {
        requireKind(MilestoneKind.LEVEL_CHECK);
        requireOpenFor(levelCheckId.equals(resultLevelCheckId));
        if (level == null) {
            throw new BadRequestException("MILESTONE_RESULT_NOT_SCORED", "The level check is not complete");
        }
        resultLevelCheckId = levelCheckId;
        resultLevel = level;
        settle(level.compareTo(requiredLevel) >= 0, now);
    }

    private void settle(boolean passed, Instant now) {
        attemptCount++;
        status = passed ? MilestoneStatus.PASSED : MilestoneStatus.FAILED;
        if (passed) {
            achievedAt = now;
        }
    }

    private void requireKind(MilestoneKind expected) {
        if (kind != expected) {
            throw new BadRequestException("MILESTONE_KIND_MISMATCH", "This milestone is not passed that way");
        }
    }

    private void requireOpenFor(boolean sameSitting) {
        if (sameSitting) {
            throw new ConflictException("MILESTONE_RESULT_RECORDED", "That sitting is already recorded");
        }
        if (status == MilestoneStatus.PASSED) {
            throw new ConflictException("MILESTONE_ALREADY_PASSED", "This milestone is already passed");
        }
    }
}
