package com.englow3.tutor.service;

import java.util.UUID;

public interface TutorReplyWriter {
    void storeReply(UUID messageId, String content, String model, Integer inputTokens, Integer outputTokens);

    void markFailed(UUID messageId, String errorCode);
}
