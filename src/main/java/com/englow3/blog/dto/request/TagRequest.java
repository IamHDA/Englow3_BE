package com.englow3.blog.dto.request;

import com.englow3.blog.dto.command.SavePostCommand;
import com.englow3.blog.entity.BlogTagKind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The value must be one of the kind's own - {@code SKILL} takes {@code WRITING}, not {@code B2} - and the entity says
 * so.
 */
public record TagRequest(@NotNull BlogTagKind kind, @NotBlank @Size(max = 30) String value) {

    SavePostCommand.Tag toCommand() {
        return new SavePostCommand.Tag(kind, value);
    }
}
