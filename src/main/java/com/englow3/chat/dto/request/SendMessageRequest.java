package com.englow3.chat.dto.request;

import java.util.UUID;

import com.englow3.chat.dto.command.SendMessageCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code clientId} is picked by the client for each message and reused when it retries, so a retry returns the first
 * message instead of sending a second. At least one of {@code body} and {@code imageUploadId} is needed; the message
 * itself enforces that, and the length limit here only refuses the obvious early.
 */
public record SendMessageRequest(@NotBlank @Size(max = 64) String clientId, @Size(max = 4000) String body,
        UUID imageUploadId) {

    public SendMessageCommand toCommand(UUID conversationId) {
        return new SendMessageCommand(conversationId, clientId, body, imageUploadId);
    }
}
