package com.englow3.roadmap.service;

import com.englow3.roadmap.entity.AdjustmentReason;
import com.englow3.roadmap.entity.LearningRoadmap;

/**
 * Asks the AI for a plan: gathers the goal, the starting point and the content that may be scheduled, and puts a
 * {@code ROADMAP_GENERATION} job on the queue. Joins the caller's transaction; nothing is sent until it commits.
 */
public interface RoadmapPlanner {

    /** The first plan of a roadmap that is {@code GENERATING}. Refuses when there is nothing to plan with. */
    void requestFirstPlan(LearningRoadmap roadmap);

    /** Replans the stages not yet passed, or refuses with the reason it cannot. */
    void requestAdjustment(LearningRoadmap roadmap, AdjustmentReason reason);

    /**
     * As {@link #requestAdjustment}, for adjustments the system decides on: when one is already in flight, today's
     * allowance is spent or there is nothing to plan with, nothing happens and the caller carries on.
     *
     * @return whether an adjustment was requested
     */
    boolean tryRequestAdjustment(LearningRoadmap roadmap, AdjustmentReason reason);
}
