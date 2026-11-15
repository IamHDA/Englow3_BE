package com.englow3.friendship.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.friendship.dto.result.FriendResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record FriendResponse(UUID userId, String displayName, @Schema(nullable = true) String avatarUrl,
        Instant friendsSince) {

    public static FriendResponse from(FriendResult result) {
        return new FriendResponse(result.userId(), result.displayName(), result.avatarUrl(), result.friendsSince());
    }
}
