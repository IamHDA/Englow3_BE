package com.englow3.ai.placement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class PlacementDtos {

    private PlacementDtos() {
    }

    record ExamSummary(UUID id, String title, String description, int durationSeconds, int questionCount) {
    }

    record StartAttemptRequest(@NotNull UUID examId) {
    }

    record StartAttemptResponse(UUID attemptId, Instant expiresAt, int questionCount) {
    }

    record SubmitAnswerRequest(@NotNull UUID questionId, @NotNull UUID optionId) {
    }

    record SubmitAttemptRequest(@NotBlank @Size(max = 200) String idempotencyKey) {
    }

    record OptionResponse(UUID id, String content, int order) {
    }

    record QuestionResponse(UUID id, String content, String skill, int order, List<OptionResponse> options) {
    }

    record AttemptResult(UUID attemptId, String status, BigDecimal rawScore, BigDecimal maxRawScore,
            BigDecimal percentage, String assessedLevel, UUID reportJobId, String aiSummary, List<String> strengths,
            List<String> learningGaps) {
    }

    record SubmitAttemptResponse(AttemptResult result, UUID reportId, UUID jobId) {
    }
}
