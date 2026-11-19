package com.englow3.chat.service;

import java.util.Set;
import java.util.UUID;

/**
 * Published inside the transaction that recalled the message. Ids only. {@code actorId} is who recalled it, so their
 * other devices can be told as well as the other person.
 */
public record ChatMessageRecalled(UUID conversationId, UUID messageId, long seq, UUID actorId, Set<UUID> recipientIds) {
}
