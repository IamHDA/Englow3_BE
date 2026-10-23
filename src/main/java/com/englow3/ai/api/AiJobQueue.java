package com.englow3.ai.api;

import java.util.UUID;

public interface AiJobQueue {
    /** Held only for the surrounding short transaction, shared by every AI feature. */
    void lockBudget(UUID userId);

    Usage usage(UUID userId);

    record Usage(int limit, int used, java.time.Instant resetsAt) {
    }

    boolean hasFailedTutorReply(UUID messageId, int revision);

    boolean hasFailedTutorTranscription(UUID recordingId);

    void enqueueTutorTranscription(UUID recordingId, String payload, String idempotencyKey, UUID userId);

    /** Scrubs content and fences a running job while preserving daily usage accounting. */
    void purgeTutorRecording(UUID recordingId, UUID userId);

    void purgeTutorData(UUID userId);

    Operations operations();

    record Operations(long pending, long running, long completedToday, long failedToday,
            java.time.Instant oldestPendingAt, String scope, Metrics metrics) {
    }

    record Metrics(long samples, Double queueP95Ms, Double completionP95Ms, long knownInputTokens,
            long knownOutputTokens, long unknownUsageJobs, java.util.List<FailureCount> failures) {
    }

    record FailureCount(String code, long count) {
    }

    boolean hasFailedProductiveAssessment(UUID attemptId, int revision);

    void enqueueProductiveAssessment(UUID attemptId, String inputPayload, String idempotencyKey, String promptVersion,
            UUID requestedByUserId);

    boolean hasDailyAllowance(UUID userId);

    int dailyRequestLimit();

    /** The same ceiling for one learner - higher while a paid plan is in force. */
    int dailyRequestLimit(UUID userId);

    void enqueueSpeechAssessment(UUID attemptId, String inputPayload, String idempotencyKey, String promptVersion,
            UUID requestedByUserId);

    void enqueueTutorReply(UUID messageId, String inputPayload, String idempotencyKey, String promptVersion,
            UUID requestedByUserId);
}
