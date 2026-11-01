package com.englow3.roadmap.dto.result;

import java.time.Instant;
import java.util.UUID;

/**
 * A level check and how far it has got.
 *
 * @param speakingFailed
 *            the spoken answer could not be graded; the learner may answer again or skip the spoken part
 * @param provisionalLevel
 *            the band from the written parts alone while the spoken part is outstanding; null otherwise
 */
public record LevelCheckResult(UUID id, String purpose, String status, UUID milestoneId, UUID examId,
        UUID speakingTaskId, UUID examAttemptId, UUID speakingAttemptId, boolean speakingSkipped,
        boolean speakingFailed, String vocabLevel, String listeningLevel, String speakingLevel, String resultLevel,
        String provisionalLevel, Instant createdAt, Instant completedAt) {
}
