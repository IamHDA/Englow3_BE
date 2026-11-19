package com.englow3.chat.dto.result;

/** {@code created} is false when the same {@code clientId} had already been sent: the first message is returned. */
public record SendMessageOutcome(ChatMessageResult message, boolean created) {
}
