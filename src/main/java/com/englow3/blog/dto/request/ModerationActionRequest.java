package com.englow3.blog.dto.request;

import com.englow3.blog.dto.command.ModerationActionCommand;
import com.englow3.blog.entity.BlogModerationActionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What to do to a post or a comment, and why: HIDE, RESTORE, REMOVE or DISMISS, always with a reason. */
public record ModerationActionRequest(@NotNull BlogModerationActionType action,
        @NotBlank @Size(max = 500) String reason) {

    public ModerationActionCommand toCommand() {
        return new ModerationActionCommand(action, reason);
    }
}
