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
        READ_ALOUD, FREE_SPEAKING
    }

    record CreateSessionRequest(@NotNull Mode mode, @Size(max = 1000) String referenceText, UUID practiceId,
            @NotBlank String contentType, @NotNull @AssertTrue Boolean consent) {
    }

    record SubmitSessionRequest(@NotBlank @Size(max = 200) String idempotencyKey) {
    }

    record CreateSessionResponse(UUID sessionId, UUID practiceId, int turnNumber, URL uploadUrl, String objectKey,
            Instant uploadUrlExpiresAt, String requiredContentType) {
    }

    record SubmitSessionResponse(UUID sessionId, UUID jobId, String status) {
    }

    record SessionSummary(UUID sessionId, String mode, String status, String recognizedText, Double pronunciation,
            Instant createdAt, Instant completedAt, Instant retentionUntil) {
    }

    record WordScore(int position, String word, Double accuracy, String errorType, Integer offsetMs, Integer durationMs,
            List<PhonemeScore> phonemes) {
    }

    record PhonemeScore(int position, String phoneme, Double accuracy) {
    }

    record SessionResult(UUID sessionId, String mode, String status, String recognizedText, Double accuracy,
            Double fluency, Double completeness, Double prosody, Double pronunciation, String grammarFeedback,
            String vocabularyFeedback, String provider, List<WordScore> words, List<Recommendation> recommendations,
            Instant retentionUntil, Instant audioDeletedAt) {
    }

    record Recommendation(int position, String contentType, String contentId, String reason) {
    }

    record RecurringError(String unitType, String unit, String errorType, int occurrenceCount, Double averageAccuracy,
            Instant firstSeenAt, Instant lastSeenAt) {
    }

    record ProgressResponse(int windowDays, Instant from, int completedSessions, Double averageAccuracy,
            Double averageFluency, Double averagePronunciation, Double pronunciationTrend) {
    }
}
