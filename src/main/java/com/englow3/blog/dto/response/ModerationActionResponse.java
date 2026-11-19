package com.englow3.blog.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.blog.dto.result.ModerationActionResult;

import io.swagger.v3.oas.annotations.media.Schema;

/** One entry of the history of moderation. {@code action} is HIDE, RESTORE, REMOVE or DISMISS. */
public record ModerationActionResponse(UUID id, String targetType, UUID targetId, String action, String reason,
        @Schema(nullable = true) BlogAuthorResponse moderator, Instant createdAt) {

    public static ModerationActionResponse from(ModerationActionResult result) {
        return new ModerationActionResponse(result.id(), result.targetType().name(), result.targetId(),
                result.action().name(), result.reason(), BlogAuthorResponse.from(result.moderator()),
                result.createdAt());
    }
}
