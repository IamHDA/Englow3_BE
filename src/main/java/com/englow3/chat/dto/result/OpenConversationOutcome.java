package com.englow3.chat.dto.result;

/** {@code created} is false when the pair already had a conversation. */
public record OpenConversationOutcome(ConversationSummaryResult conversation, boolean created) {
}
