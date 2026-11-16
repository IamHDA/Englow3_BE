package com.englow3.chat.dto.command;

import java.util.UUID;

/** {@code body} and {@code imageUploadId} are each optional; a message needs at least one. */
public record SendMessageCommand(UUID conversationId, String clientId, String body, UUID imageUploadId) {
}
