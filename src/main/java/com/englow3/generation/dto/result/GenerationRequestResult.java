package com.englow3.generation.dto.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.generation.entity.GenerationKind;
import com.englow3.generation.entity.GenerationStatus;

/**
 * A request as the admin page shows it.
 *
 * @param title
 *            the set's title once the plan is written, null before
 * @param lastProblems
 *            what the model got wrong on its last attempt at the current step; empty when nothing is pending
 * @param resumeCount
 *            how many times this request was picked up again after failing
 * @param resumable
 *            the request failed and may still be resumed from the step it stopped at
 */
public record GenerationRequestResult(UUID id, GenerationKind kind, GenerationStatus status, String step, int stepIndex,
        int stepsTotal, int attempt, String brief, UUID requestedByUserId, String requestedByName, String title,
        UUID examId, List<UUID> taskIds, UUID fullTestId, String failureCode, String failureMessage,
        List<String> lastProblems, int resumeCount, boolean resumable, Instant createdAt, Instant updatedAt,
        Instant completedAt) {
}
