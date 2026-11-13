package com.englow3.roadmap.service;

import java.util.Optional;
import java.util.UUID;

import com.englow3.roadmap.dto.result.LevelCheckResult;
import com.englow3.roadmap.entity.LevelCheckPurpose;

/**
 * The three-part level check. The learner sits the paper in the exam module and answers the spoken task in the
 * assessment module; this keeps the two sittings together and reads their grades on every look, so a result shows up as
 * soon as it exists without either module knowing the roadmap is waiting.
 */
public interface LevelCheckService {

    /** Starts a check, or returns the one already open for the same purpose. */
    LevelCheckResult start(LevelCheckPurpose purpose, UUID milestoneId);

    LevelCheckResult get(UUID levelCheckId);

    Optional<LevelCheckResult> latest();

    LevelCheckResult attachExamAttempt(UUID levelCheckId, UUID examAttemptId);

    LevelCheckResult attachSpeakingAttempt(UUID levelCheckId, UUID assessmentAttemptId);

    LevelCheckResult skipSpeaking(UUID levelCheckId);
}
