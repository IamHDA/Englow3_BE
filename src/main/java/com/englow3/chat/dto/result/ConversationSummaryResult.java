package com.englow3.chat.dto.result;

import java.time.Instant;
import java.util.UUID;

/**
 * One conversation as the caller sees it. {@code otherUser} is null once the other person's account is erased.
 * {@code canSend} is only worked out for a single conversation (it asks the friendship module), never for a list.
 */
public record ConversationSummaryResult(UUID id, OtherUser otherUser, long lastSeq, long lastReadSeq, long unreadCount,
        LastMessage lastMessage, Boolean canSend) {

    public record OtherUser(UUID userId, String displayName, String avatarUrl) {
    }

    /** {@code preview} is the start of the text, null for an image only or a recalled message. */
    public record LastMessage(long seq, UUID senderId, String preview, boolean hasImage, boolean recalled,
            Instant sentAt) {
    }
}
