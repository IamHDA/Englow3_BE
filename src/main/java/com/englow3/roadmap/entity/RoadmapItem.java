package com.englow3.roadmap.entity;

import java.util.UUID;

import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * A piece of published content scheduled in a stage. Plain data: whether it is done is worked out from the learner's
 * activity each time, never stored here.
 */
@Entity
@Table(name = "roadmap_items")
@Getter
public class RoadmapItem extends BasePersistedEntity {

    private UUID stageId;
    private int orderNo;
    @Enumerated(EnumType.STRING)
    private RoadmapContentKind contentKind;
    private UUID contentId;
    private String title;
    private boolean required;
    private int targetPercent;
    private int plannedWeek;
    private int estimatedMinutes;

    protected RoadmapItem() {
    }

    public static RoadmapItem of(UUID id, UUID stageId, int orderNo, RoadmapContentKind kind, UUID contentId,
            String title, boolean required, int targetPercent, int plannedWeek, int estimatedMinutes) {
        RoadmapItem item = new RoadmapItem();
        item.id = id;
        item.stageId = stageId;
        item.orderNo = orderNo;
        item.contentKind = kind;
        item.contentId = contentId;
        item.title = title.length() > 200 ? title.substring(0, 200) : title;
        item.required = required;
        item.targetPercent = targetPercent;
        item.plannedWeek = plannedWeek;
        item.estimatedMinutes = estimatedMinutes;
        return item;
    }
}
