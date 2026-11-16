package com.englow3.chat.service;

import java.util.Set;
import java.util.UUID;

/** Published inside the transaction that moved a read mark forward, with the mark now stored. Ids and a seq only. */
public record ChatReadAdvanced(UUID conversationId, UUID readerId, long upToSeq, Set<UUID> recipientIds) {
}
