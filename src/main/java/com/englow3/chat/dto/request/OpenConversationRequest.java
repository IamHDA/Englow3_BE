package com.englow3.chat.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record OpenConversationRequest(@NotNull UUID friendId) {
}
