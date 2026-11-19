package com.englow3.blog.dto.command;

import com.englow3.blog.entity.BlogModerationActionType;

public record ModerationActionCommand(BlogModerationActionType action, String reason) {
}
