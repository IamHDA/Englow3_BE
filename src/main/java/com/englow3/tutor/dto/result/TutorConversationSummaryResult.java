package com.englow3.tutor.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.tutor.entity.TutorConversation;
import io.swagger.v3.oas.annotations.media.Schema;

/** A row in the conversation list. No messages: the list shows what a thread is about, not what is in it. */
public record TutorConversationSummaryResult(UUID id, String title, @Schema(nullable = true) String topic,
        int messageCount, Instant lastMessageAt, Instant createdAt) {

    public static TutorConversationSummaryResult of(TutorConversation conversation) {
        return new TutorConversationSummaryResult(conversation.getId(), conversation.getTitle(),
                conversation.getTopic(), conversation.getMessageCount(), conversation.getLastMessageAt(),
                conversation.getCreatedAt());
    }
}
