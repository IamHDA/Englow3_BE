package com.englow3.roadmap.service;

import java.util.Optional;
import java.util.UUID;

import com.englow3.roadmap.dto.result.RoadmapResult;

/** The signed-in learner's own roadmap. */
public interface RoadmapService {

    /** The plan in play, else the newest one (failed or finished), else nothing. */
    Optional<RoadmapResult> current();

    /**
     * A plan for the learner's current goal. With a plan already in play for the same kind of goal, this is the same
     * plan - adjusted if the score, level or date changed; for a different goal, the old plan is superseded.
     */
    RoadmapResult create();

    void abandon();

    /** A sitting of the current stage's mock paper, taken as that milestone's result. */
    RoadmapResult recordMockSitting(UUID milestoneId, UUID examAttemptId);
}
