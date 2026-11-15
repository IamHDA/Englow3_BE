package com.englow3.friendship.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.friendship.dto.result.BlockedUserResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record BlockedUserResponse(UUID userId, String displayName, @Schema(nullable = true) String avatarUrl,
        Instant blockedAt) {

    public static BlockedUserResponse from(BlockedUserResult result) {
        return new BlockedUserResponse(result.userId(), result.displayName(), result.avatarUrl(), result.blockedAt());
    }
}
