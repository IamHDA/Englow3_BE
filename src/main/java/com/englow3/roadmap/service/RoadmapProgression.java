package com.englow3.roadmap.service;

import java.util.Optional;
import java.util.UUID;

import com.englow3.roadmap.entity.LearningRoadmap;
import com.englow3.roadmap.entity.RoadmapMilestone;
import com.englow3.roadmap.entity.RoadmapStage;

/** Moves a roadmap on once a milestone has a result: the next stage, the finish, or a replan. */
public interface RoadmapProgression {

    /** A milestone with its stage and roadmap, all locked, the roadmap the learner's own and active. */
    record MilestoneContext(LearningRoadmap roadmap, RoadmapStage stage, RoadmapMilestone milestone) {
    }

    /** The milestone of the learner's current stage, or a refusal saying why it cannot be taken now. */
    MilestoneContext locate(UUID userId, UUID milestoneId);

    /** As {@link #locate}, empty instead of refusing - for a result arriving after the plan moved on. */
    Optional<MilestoneContext> find(UUID userId, UUID milestoneId);

    /** Acts on the result just recorded on the milestone. */
    void settle(MilestoneContext context);
}
