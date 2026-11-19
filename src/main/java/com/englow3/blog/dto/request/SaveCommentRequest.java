package com.englow3.blog.dto.request;

import com.englow3.blog.dto.command.SaveCommentCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveCommentRequest(@NotBlank @Size(max = 2000) String body) {

    public SaveCommentCommand toCommand() {
        return new SaveCommentCommand(body);
    }
}
