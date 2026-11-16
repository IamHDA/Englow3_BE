package com.englow3.chat.dto.projection;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the conversation list. {@code otherUserId} is null when the other person's account is erased; the
 * {@code last*} columns are null only for a conversation with no messages, which the list leaves out.
 */
public record ConversationRow(UUID id, long lastSeq, Instant lastMessageAt, long lastReadSeq, UUID otherUserId,
        Long lastMessageSeq, UUID lastSenderId, String lastBodyPreview, boolean lastHasImage, boolean lastRecalled,
        Instant lastSentAt) {
}
