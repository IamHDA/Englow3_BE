package com.englow3.ai.writing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class WritingDtos {

    private WritingDtos() {
    }

    record TaskSummary(String taskId, String taskType, String prompt, Integer minWords, Integer maxWords,
            String rubricId) {
    }

    record CreateSubmissionRequest(@NotBlank @Size(max = 100) String taskId,
            @NotBlank @Size(max = 12000) String responseText, @NotBlank @Size(max = 200) String idempotencyKey) {
    }

    record SubmissionAccepted(UUID submissionId, UUID jobId, String status, int wordCount) {
    }

    record Assessment(BigDecimal overallScore, String cefrLevel, String summary, JsonNode criterionScores,
            List<String> strengths, List<String> improvements, String correctedResponse, String sampleRevision) {
    }

    record SubmissionResult(UUID submissionId, String taskId, String status, int wordCount, UUID jobId,
            Assessment assessment, Instant createdAt, Instant completedAt) {
    }

    record SubmissionSummary(UUID submissionId, String taskId, String status, int wordCount, BigDecimal overallScore,
            String cefrLevel, Instant createdAt, Instant completedAt) {
    }
}
