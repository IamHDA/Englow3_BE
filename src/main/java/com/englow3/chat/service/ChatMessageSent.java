package com.englow3.chat.service;

import java.util.Set;
import java.util.UUID;

/**
 * Published inside the transaction that wrote the message. Carries ids only, never the text: a listener reads the
 * message itself after commit, so what it pushes is what was stored.
 */
public record ChatMessageSent(UUID conversationId, UUID messageId, long seq, UUID senderId, Set<UUID> recipientIds) {
}
