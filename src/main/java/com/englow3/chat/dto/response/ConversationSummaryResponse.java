package com.englow3.chat.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.chat.dto.result.ConversationSummaryResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code otherUser} is null when the other person has erased their account. {@code canSend} is present only when one
 * conversation is asked for or opened, not in the list. {@code unreadCount} is {@code max(0, lastSeq - lastReadSeq)}.
 */
public record ConversationSummaryResponse(UUID id, @Schema(nullable = true) OtherUser otherUser, long lastSeq,
        long lastReadSeq, long unreadCount, @Schema(nullable = true) LastMessage lastMessage,
        @Schema(nullable = true) Boolean canSend) {

    public record OtherUser(UUID userId, String displayName, @Schema(nullable = true) String avatarUrl) {
    }

    public record LastMessage(long seq, @Schema(nullable = true) UUID senderId, @Schema(nullable = true) String preview,
            boolean hasImage, boolean recalled, Instant sentAt) {
    }

    public static ConversationSummaryResponse from(ConversationSummaryResult result) {
        OtherUser otherUser = result.otherUser() == null ? null
                : new OtherUser(result.otherUser().userId(), result.otherUser().displayName(),
                        result.otherUser().avatarUrl());
        LastMessage lastMessage = result.lastMessage() == null ? null
                : new LastMessage(result.lastMessage().seq(), result.lastMessage().senderId(),
                        result.lastMessage().preview(), result.lastMessage().hasImage(),
                        result.lastMessage().recalled(), result.lastMessage().sentAt());
        return new ConversationSummaryResponse(result.id(), otherUser, result.lastSeq(), result.lastReadSeq(),
                result.unreadCount(), lastMessage, result.canSend());
    }
}
