package com.englow3.blog.dto.request;

import com.englow3.blog.dto.command.StartImageUploadCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record StartImageUploadRequest(@NotBlank String contentType, @Positive long contentLength) {

    public StartImageUploadCommand toCommand() {
        return new StartImageUploadCommand(contentType, contentLength);
    }
}
