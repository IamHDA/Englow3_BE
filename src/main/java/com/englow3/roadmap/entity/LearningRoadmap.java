package com.englow3.roadmap.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

/**
 * One learner's plan towards one goal. The goal is copied in when the plan is made, so a plan keeps meaning what it
 * meant even after the learner edits their profile; a changed goal is an adjustment or a new plan, never a silent edit.
 * <p>
 * Every request to the AI - the first plan or an adjustment - is tagged with a fresh token. Only the result carrying
 * the current token may change the plan, so a slow job overtaken by a newer request cannot overwrite it.
 */
@Entity
@Table(name = "learning_roadmaps")
@Getter
public class LearningRoadmap extends BasePersistedEntity {

    private UUID userId;
    @Enumerated(EnumType.STRING)
    private RoadmapStatus status;
    @Enumerated(EnumType.STRING)
    private RoadmapGoalType goalType;
    private String targetCertificate;
    private BigDecimal targetScore;
    @Enumerated(EnumType.STRING)
    private CefrLevel targetLevel;
    private LocalDate targetDate;
    @Enumerated(EnumType.STRING)
    private CefrLevel startLevel;
    private BigDecimal startScore;
    private Integer dailyMinutes;
    private String modelName;
    private String promptVersion;
    private String failureCode;
    private UUID pendingJobToken;
    @Enumerated(EnumType.STRING)
    private AdjustmentReason pendingReason;
    @Version
    private long version;
    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
    private Instant completedAt;

    protected LearningRoadmap() {
    }

    public static LearningRoadmap forCertificate(UUID id, UUID userId, String certificate, BigDecimal targetScore,
            LocalDate targetDate, CefrLevel startLevel, BigDecimal startScore, Integer dailyMinutes, UUID token) {
        if (certificate == null || targetScore == null) {
            throw new BadRequestException("ROADMAP_GOAL_INCOMPLETE",
                    "A certificate goal needs a certificate and score");
        }
        LearningRoadmap roadmap = base(id, userId, RoadmapGoalType.CERTIFICATE, targetDate, startLevel, dailyMinutes,
                token);
        roadmap.targetCertificate = certificate;
        roadmap.targetScore = targetScore;
        roadmap.startScore = startScore;
        return roadmap;
    }

    public static LearningRoadmap forLevel(UUID id, UUID userId, CefrLevel targetLevel, LocalDate targetDate,
            CefrLevel startLevel, Integer dailyMinutes, UUID token) {
        if (targetLevel == null) {
            throw new BadRequestException("ROADMAP_GOAL_INCOMPLETE", "A level goal needs a target level");
        }
        if (startLevel != null && !targetLevel.isAbove(startLevel)) {
            throw new BadRequestException("ROADMAP_TARGET_NOT_ABOVE_START",
                    "The target level must be above the starting level");
        }
        LearningRoadmap roadmap = base(id, userId, RoadmapGoalType.LEVEL, targetDate, startLevel, dailyMinutes, token);
        roadmap.targetLevel = targetLevel;
        return roadmap;
    }

    private static LearningRoadmap base(UUID id, UUID userId, RoadmapGoalType goalType, LocalDate targetDate,
            CefrLevel startLevel, Integer dailyMinutes, UUID token) {
        if (startLevel == null) {
            throw new BadRequestException("ROADMAP_LEVEL_UNKNOWN", "The starting level is not known yet");
        }
        LearningRoadmap roadmap = new LearningRoadmap();
        roadmap.id = Objects.requireNonNull(id);
        roadmap.userId = Objects.requireNonNull(userId);
        roadmap.goalType = goalType;
        roadmap.targetDate = targetDate;
        roadmap.startLevel = startLevel;
        roadmap.dailyMinutes = dailyMinutes;
        roadmap.status = RoadmapStatus.GENERATING;
        roadmap.pendingJobToken = Objects.requireNonNull(token);
        return roadmap;
    }

    /** Whether a job result tagged with {@code token} is the one this plan is waiting for. */
    public boolean isWaitingFor(UUID token) {
        return token != null && token.equals(pendingJobToken);
    }

    public boolean isAdjusting() {
        return status == RoadmapStatus.ACTIVE && pendingJobToken != null;
    }

    /** A failed first plan, asked for again. */
    public void retryGeneration(UUID token) {
        if (status != RoadmapStatus.FAILED) {
            throw new ConflictException("ROADMAP_NOT_FAILED", "Only a roadmap that could not be made can be retried");
        }
        status = RoadmapStatus.GENERATING;
        failureCode = null;
        pendingJobToken = Objects.requireNonNull(token);
    }

    /** The first plan arrived and passed every check. */
    public void activate(UUID token, String modelName, String promptVersion) {
        requireWaitingFor(token);
        if (status != RoadmapStatus.GENERATING) {
            throw new ConflictException("ROADMAP_NOT_GENERATING", "This roadmap is not waiting for a first plan");
        }
        status = RoadmapStatus.ACTIVE;
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        clearPending();
    }

    /**
     * The AI gave up. A first plan fails the roadmap; a failed adjustment leaves the plan in force as it was, since an
     * old plan is more use to the learner than none.
     *
     * @return false when the result is stale and nothing changed
     */
    public boolean giveUp(UUID token, String code) {
        if (!isWaitingFor(token)) {
            return false;
        }
        if (status == RoadmapStatus.GENERATING) {
            status = RoadmapStatus.FAILED;
        }
        failureCode = code;
        clearPending();
        return true;
    }

    /** Only a plan in force is adjusted, and one adjustment at a time. */
    public void requestAdjustment(AdjustmentReason reason, UUID token) {
        if (status != RoadmapStatus.ACTIVE) {
            throw new ConflictException("ROADMAP_NOT_ADJUSTABLE", "Only an active roadmap can be adjusted");
        }
        if (pendingJobToken != null) {
            throw new ConflictException("ROADMAP_ADJUSTMENT_IN_PROGRESS", "The roadmap is already being adjusted");
        }
        pendingReason = Objects.requireNonNull(reason);
        pendingJobToken = Objects.requireNonNull(token);
        failureCode = null;
    }

    /**
     * Drops the adjustment in flight: the learner passed the stage it was replanning, so its result would replan from a
     * point already behind them. Its late result then carries a stale token and is ignored.
     */
    public void cancelAdjustment() {
        if (isAdjusting()) {
            clearPending();
        }
    }

    public void applyAdjustment(UUID token, String modelName, String promptVersion) {
        requireWaitingFor(token);
        if (status != RoadmapStatus.ACTIVE) {
            throw new ConflictException("ROADMAP_NOT_ADJUSTABLE", "Only an active roadmap can be adjusted");
        }
        this.modelName = modelName;
        this.promptVersion = promptVersion;
        clearPending();
    }

    /** A new score, level or date of the same kind of goal. The plan is then adjusted to it. */
    public void changeGoal(BigDecimal targetScore, CefrLevel targetLevel, LocalDate targetDate, Integer dailyMinutes) {
        requireOpen();
        if (goalType == RoadmapGoalType.CERTIFICATE) {
            this.targetScore = Objects.requireNonNull(targetScore);
        } else {
            if (targetLevel == null || !targetLevel.isAbove(startLevel)) {
                throw new BadRequestException("ROADMAP_TARGET_NOT_ABOVE_START",
                        "The target level must be above the starting level");
            }
            this.targetLevel = targetLevel;
        }
        this.targetDate = targetDate;
        this.dailyMinutes = dailyMinutes;
    }

    /** Finished, which only the last stage's milestone being passed can mean. */
    public void complete(RoadmapMilestone finalMilestone, Instant now) {
        if (status != RoadmapStatus.ACTIVE) {
            throw new ConflictException("ROADMAP_NOT_ADJUSTABLE", "Only an active roadmap can be completed");
        }
        if (finalMilestone == null || finalMilestone.getStatus() != MilestoneStatus.PASSED) {
            throw new ConflictException("ROADMAP_FINAL_MILESTONE_PENDING",
                    "A roadmap is complete only when its last milestone is passed");
        }
        status = RoadmapStatus.COMPLETED;
        completedAt = now;
        clearPending();
    }

    public void supersede(Instant now) {
        requireOpen();
        status = RoadmapStatus.SUPERSEDED;
        completedAt = now;
        clearPending();
    }

    public void abandon(Instant now) {
        requireOpen();
        status = RoadmapStatus.ABANDONED;
        completedAt = now;
        clearPending();
    }

    private void requireOpen() {
        if (!status.isOpen()) {
            throw new ConflictException("ROADMAP_CLOSED", "This roadmap is finished and can no longer change");
        }
    }

    private void requireWaitingFor(UUID token) {
        if (!isWaitingFor(token)) {
            throw new ConflictException("ROADMAP_RESULT_STALE", "This result is for a request that was replaced");
        }
    }

    private void clearPending() {
        pendingJobToken = null;
        pendingReason = null;
    }
}
