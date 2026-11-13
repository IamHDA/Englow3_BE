package com.englow3.generation.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.generation.dto.result.GenerationRequestResult;
import com.englow3.generation.entity.GenerationKind;
import com.englow3.generation.entity.GenerationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record GenerationRequestResponse(UUID id, GenerationKind kind, GenerationStatus status,
        @Schema(nullable = true, description = "The step running, or where the request stopped") String step,
        int stepIndex, int stepsTotal, int attempt, @Schema(nullable = true) String brief, UUID requestedByUserId,
        @Schema(nullable = true) String requestedByName, @Schema(nullable = true) String title,
        @Schema(nullable = true) UUID examId, List<UUID> taskIds, @Schema(nullable = true) UUID fullTestId,
        @Schema(nullable = true) String failureCode, @Schema(nullable = true) String failureMessage,
        List<String> lastProblems, Instant createdAt, Instant updatedAt, @Schema(nullable = true) Instant completedAt) {

    public static GenerationRequestResponse from(GenerationRequestResult r) {
        return new GenerationRequestResponse(r.id(), r.kind(), r.status(), r.step(), r.stepIndex(), r.stepsTotal(),
                r.attempt(), r.brief(), r.requestedByUserId(), r.requestedByName(), r.title(), r.examId(), r.taskIds(),
                r.fullTestId(), r.failureCode(), r.failureMessage(), r.lastProblems(), r.createdAt(), r.updatedAt(),
                r.completedAt());
    }
}
