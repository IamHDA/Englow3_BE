package com.englow3.friendship.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.friendship.dto.result.FriendRequestResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record FriendRequestResponse(UUID requestId, UUID userId, String displayName,
        @Schema(nullable = true) String avatarUrl, Instant requestedAt) {

    public static FriendRequestResponse from(FriendRequestResult result) {
        return new FriendRequestResponse(result.requestId(), result.userId(), result.displayName(), result.avatarUrl(),
                result.requestedAt());
    }
}
