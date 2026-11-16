package com.englow3.blog.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.blog.entity.BlogModerationActionType;
import com.englow3.blog.entity.BlogTargetType;

/** One entry of the history of moderation, whoever it was about. {@code moderator} is null if no profile is found. */
public record ModerationActionResult(UUID id, BlogTargetType targetType, UUID targetId, BlogModerationActionType action,
        String reason, BlogAuthorResult moderator, Instant createdAt) {
}
