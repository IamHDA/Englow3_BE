package com.englow3.roadmap.entity;

public enum RoadmapStageStatus {
    LOCKED, CURRENT, PASSED,
    /** Taken out of the plan by an adjustment. Kept, so the learner can see what changed. */
    REPLACED
}
