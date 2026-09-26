package com.englow3.ai.api;

import java.util.UUID;

public interface AiJobQueue {
    boolean hasDailyAllowance(UUID userId);

    int dailyRequestLimit();

    void enqueueSpeechAssessment(UUID attemptId, String inputPayload, String idempotencyKey, String promptVersion,
            UUID requestedByUserId);

    void enqueueTutorReply(UUID messageId, String inputPayload, String idempotencyKey, String promptVersion,
            UUID requestedByUserId);
}
