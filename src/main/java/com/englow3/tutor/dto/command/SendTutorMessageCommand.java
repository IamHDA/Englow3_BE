package com.englow3.tutor.dto.command;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param conversationId
 *            null starts a new thread. One endpoint rather than two, because "send a message" is one thing the learner
 *            does and whether a thread already exists is not their concern.
 */
public record SendTutorMessageCommand(UUID conversationId, @NotBlank @Size(max = 4_000) String message,
        @Size(max = 60) String topic) {
}
