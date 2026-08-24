package com.englow3.ai.speaking;

import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class SpeakingDtos {

    private SpeakingDtos() {
    }

    enum Mode {
        READ_ALOUD,
        FREE_SPEAKING
    }

    record CreateSessionRequest(@NotNull Mode mode,
            @Size(max = 1000) String referenceText,
            @NotBlank String contentType,
            @NotNull @AssertTrue Boolean consent) {
    }

    record SubmitSessionRequest(@NotBlank @Size(max = 200) String idempotencyKey) {
    }

    record CreateSessionResponse(UUID sessionId, URL uploadUrl, String objectKey, Instant uploadUrlExpiresAt,
            String requiredContentType) {
    }

    record SubmitSessionResponse(UUID sessionId, UUID jobId, String status) {
    }

    record SessionSummary(UUID sessionId, String mode, String status, String recognizedText, Double pronunciation,
            Instant createdAt, Instant completedAt, Instant retentionUntil) {
    }

    record WordScore(int position, String word, Double accuracy, String errorType, Integer offsetMs,
            Integer durationMs) {
    }

    record SessionResult(UUID sessionId, String mode, String status, String recognizedText, Double accuracy,
            Double fluency, Double completeness, Double prosody, Double pronunciation, String grammarFeedback,
            String vocabularyFeedback, List<WordScore> words, Instant retentionUntil) {
    }
}
