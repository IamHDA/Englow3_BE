package com.englow3.chat.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.chat.dto.result.ChatMessageResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code senderId} is null for a deleted user. {@code body} and {@code imageUrl} are null once recalled;
 * {@code imageUrl} is a short-lived link. {@code clientId} comes back only on the caller's own messages.
 */
public record ChatMessageResponse(UUID id, UUID conversationId, long seq, @Schema(nullable = true) UUID senderId,
        @Schema(nullable = true) String body, @Schema(nullable = true) String imageUrl, boolean recalled,
        Instant sentAt, @Schema(nullable = true) String clientId) {

    public static ChatMessageResponse from(ChatMessageResult result) {
        return new ChatMessageResponse(result.id(), result.conversationId(), result.seq(), result.senderId(),
                result.body(), result.imageUrl(), result.recalled(), result.sentAt(), result.clientId());
    }
}
