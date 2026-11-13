package com.englow3.roadmap.service;

import java.util.List;
import java.util.UUID;

import com.englow3.roadmap.helper.RoadmapPlan.ProposedStage;

/** Writes what the generator produced. Called by the AI worker under its claim; no external I/O. */
public interface RoadmapPlanWriter {

    /** Applies a checked plan, unless the roadmap has since stopped waiting for this request. */
    void apply(UUID roadmapId, UUID token, List<ProposedStage> stages, String modelName, String promptVersion);

    /** The generator gave up: a first plan fails the roadmap; a failed adjustment leaves the plan as it was. */
    void giveUp(UUID roadmapId, String errorCode);
}
