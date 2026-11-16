package com.englow3.chat.dto.request;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.NotNull;

/**
 * The only frame a client sends on the chat socket: "I am typing in this conversation" or "I stopped". A client that
 * adds fields later does not break it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TypingFrame(@NotNull UUID conversationId, boolean active) {
}
