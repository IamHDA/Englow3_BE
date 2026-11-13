package com.englow3.tutor.service;

import java.util.UUID;

public interface TutorReplyWriter {
    void storeSummaryReply(UUID messageId, int revision, long epoch, int throughOrder, String content, String model,
            Integer inputTokens, Integer outputTokens);

    UUID startStream(UUID messageId, int revision);

    boolean partial(UUID messageId, int revision, UUID epoch, String text);

    void storeReply(UUID messageId, String content, String model, Integer inputTokens, Integer outputTokens);

    void markFailed(UUID messageId, String errorCode);

    boolean isPending(UUID messageId, int revision);

    void storeReply(UUID messageId, int revision, String content, String model, Integer inputTokens,
            Integer outputTokens);
}
