package com.englow3.roadmap.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/** One step of a roadmap, from one band to the next, closed by its milestone. */
@Entity
@Table(name = "roadmap_stages")
@Getter
public class RoadmapStage extends BasePersistedEntity {

    private UUID roadmapId;
    private int orderNo;
    private String title;
    @Column(columnDefinition = "text")
    private String summary;
    @Enumerated(EnumType.STRING)
    private CefrLevel fromLevel;
    @Enumerated(EnumType.STRING)
    private CefrLevel toLevel;
    private String focusSkills;
    private LocalDate plannedStart;
    private LocalDate plannedEnd;
    @Enumerated(EnumType.STRING)
    private RoadmapStageStatus status;
    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    protected RoadmapStage() {
    }

    public static RoadmapStage plan(UUID id, UUID roadmapId, int orderNo, String title, String summary,
            CefrLevel fromLevel, CefrLevel toLevel, String focusSkills, LocalDate plannedStart, LocalDate plannedEnd,
            boolean current) {
        if (orderNo < 1 || fromLevel == null || toLevel == null || plannedStart == null || plannedEnd == null
                || plannedEnd.isBefore(plannedStart) || toLevel.compareTo(fromLevel) < 0) {
            throw new BadRequestException("ROADMAP_STAGE_INVALID", "A stage needs a forward level range and dates");
        }
        RoadmapStage stage = new RoadmapStage();
        stage.id = Objects.requireNonNull(id);
        stage.roadmapId = Objects.requireNonNull(roadmapId);
        stage.orderNo = orderNo;
        stage.title = title == null || title.isBlank() ? "%s → %s".formatted(fromLevel, toLevel) : title.strip();
        stage.summary = summary == null ? "" : summary.strip();
        stage.fromLevel = fromLevel;
        stage.toLevel = toLevel;
        stage.focusSkills = focusSkills == null ? "" : focusSkills;
        stage.plannedStart = plannedStart;
        stage.plannedEnd = plannedEnd;
        stage.status = current ? RoadmapStageStatus.CURRENT : RoadmapStageStatus.LOCKED;
        return stage;
    }

    /** Weeks the stage spans, counting a part week as a week. */
    public int weeks() {
        return (int) ((ChronoUnit.DAYS.between(plannedStart, plannedEnd) + 7) / 7);
    }

    public boolean isInForce() {
        return status != RoadmapStageStatus.REPLACED;
    }

    /** The previous stage was passed; this one is now the one being worked on. */
    public void open() {
        if (status != RoadmapStageStatus.LOCKED) {
            throw new ConflictException("ROADMAP_STAGE_NOT_LOCKED", "Only a locked stage can be opened");
        }
        status = RoadmapStageStatus.CURRENT;
    }

    /** Passed, which only its own milestone being passed can mean. */
    public void pass(RoadmapMilestone milestone) {
        if (status != RoadmapStageStatus.CURRENT) {
            throw new ConflictException("ROADMAP_STAGE_NOT_CURRENT", "Only the current stage can be passed");
        }
        if (milestone == null || !id.equals(milestone.getStageId())
                || milestone.getStatus() != MilestoneStatus.PASSED) {
            throw new ConflictException("ROADMAP_MILESTONE_NOT_PASSED", "A stage is passed only by its milestone");
        }
        status = RoadmapStageStatus.PASSED;
    }

    /** Taken out by an adjustment. What the learner has already passed stays as it was. */
    public void replace() {
        if (status == RoadmapStageStatus.PASSED) {
            throw new ConflictException("ROADMAP_STAGE_PASSED", "A passed stage is never replaced");
        }
        status = RoadmapStageStatus.REPLACED;
    }
}
