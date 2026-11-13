package com.englow3.roadmap.entity;

public enum RoadmapStatus {
    /** Waiting for the first plan from the AI. */
    GENERATING,
    /** A plan is in force. An adjustment may be in flight; the plan stays usable meanwhile. */
    ACTIVE,
    /** The first plan could not be made. The learner may ask again. */
    FAILED, COMPLETED,
    /** Replaced by a plan for a different goal. */
    SUPERSEDED,
    /** The learner turned the roadmap off. */
    ABANDONED;

    public boolean isOpen() {
        return this == GENERATING || this == ACTIVE;
    }
}
