package com.englow3.chat.service;

import java.util.Set;
import java.util.UUID;

/** Published inside the transaction that recalled the message. Ids only. */
public record ChatMessageRecalled(UUID conversationId, UUID messageId, long seq, Set<UUID> recipientIds) {
}
