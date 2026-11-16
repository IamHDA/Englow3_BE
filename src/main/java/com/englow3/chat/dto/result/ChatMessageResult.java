package com.englow3.chat.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.chat.entity.ChatMessage;

/**
 * {@code senderId} is null once the sender's account is erased. {@code body} and {@code imageUrl} are null for a
 * recalled message. {@code clientId} is set only on the caller's own messages.
 */
public record ChatMessageResult(UUID id, UUID conversationId, long seq, UUID senderId, String body, String imageUrl,
        boolean recalled, Instant sentAt, String clientId) {

    public static ChatMessageResult of(ChatMessage message, String imageUrl, UUID viewerId) {
        boolean own = message.getSenderId() != null && message.getSenderId().equals(viewerId);
        return new ChatMessageResult(message.getId(), message.getConversationId(), message.getSeq(),
                message.getSenderId(), message.getBody(), imageUrl, message.isRecalled(), message.getSentAt(),
                own ? message.getClientId() : null);
    }
}
