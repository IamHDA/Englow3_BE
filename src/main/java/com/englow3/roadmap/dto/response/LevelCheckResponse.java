package com.englow3.roadmap.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.roadmap.dto.result.LevelCheckResult;
import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.entity.LevelCheckPurpose;
import com.englow3.roadmap.entity.LevelCheckStatus;

public record LevelCheckResponse(UUID id, LevelCheckPurpose purpose, LevelCheckStatus status, UUID milestoneId,
        UUID examId, UUID speakingTaskId, UUID examAttemptId, UUID speakingAttemptId, boolean speakingSkipped,
        boolean speakingFailed, CefrLevel vocabLevel, CefrLevel listeningLevel, CefrLevel speakingLevel,
        CefrLevel resultLevel, CefrLevel provisionalLevel, Instant createdAt, Instant completedAt) {

    public static LevelCheckResponse from(LevelCheckResult r) {
        return new LevelCheckResponse(r.id(), r.purpose(), r.status(), r.milestoneId(), r.examId(), r.speakingTaskId(),
                r.examAttemptId(), r.speakingAttemptId(), r.speakingSkipped(), r.speakingFailed(), r.vocabLevel(),
                r.listeningLevel(), r.speakingLevel(), r.resultLevel(), r.provisionalLevel(), r.createdAt(),
                r.completedAt());
    }
}
