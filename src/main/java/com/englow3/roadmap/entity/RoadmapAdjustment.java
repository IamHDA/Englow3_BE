package com.englow3.roadmap.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/** Why the plan changed and what it looked like either side, so the learner can see the reason. Plain history. */
@Entity
@Table(name = "roadmap_adjustments")
@Getter
public class RoadmapAdjustment extends BasePersistedEntity {

    private UUID roadmapId;
    @Enumerated(EnumType.STRING)
    private AdjustmentReason reason;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String before;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String after;
    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    protected RoadmapAdjustment() {
    }

    public static RoadmapAdjustment of(UUID id, UUID roadmapId, AdjustmentReason reason, String before, String after) {
        RoadmapAdjustment adjustment = new RoadmapAdjustment();
        adjustment.id = id;
        adjustment.roadmapId = roadmapId;
        adjustment.reason = reason;
        adjustment.before = before;
        adjustment.after = after;
        return adjustment;
    }
}
