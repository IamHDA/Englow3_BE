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

    /**
     * The learner sits the current stage's mock paper on the usual exam screens. The newest scored full sitting since
     * the stage began, if not already counted, is taken as the milestone's result and acted on.
     *
     * @return the milestone, locked and settled, when a sitting was recorded
     */
    Optional<MilestoneContext> recordLatestMockSitting(LearningRoadmap roadmap);

    /**
     * Brings an active roadmap up to date with the learner's activity: counts a new sitting of the current mock paper,
     * and asks for a replan when the current stage is a week past its planned end. Joins the caller's transaction.
     *
     * @return the roadmap as it now stands (locked, when anything changed)
     */
    LearningRoadmap catchUp(LearningRoadmap roadmap);

    /**
     * When the stage became the one being worked on: the moment the previous stage's milestone was passed, or the
     * stage's creation for the first. Only a sitting since then can close it - otherwise the sitting that passed one
     * stage would be counted again for the next stage's mock on the same paper.
     */
    java.time.Instant openedAt(RoadmapStage stage);

    /** Acts on the result just recorded on the milestone. */
    void settle(MilestoneContext context);
}
