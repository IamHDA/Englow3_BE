package com.englow3.chat.dto.request;

import java.util.UUID;

import com.englow3.chat.dto.command.StartImageUploadCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record StartImageUploadRequest(@NotBlank String contentType, @Positive long contentLength) {

    public StartImageUploadCommand toCommand(UUID conversationId) {
        return new StartImageUploadCommand(conversationId, contentType, contentLength);
    }
}
