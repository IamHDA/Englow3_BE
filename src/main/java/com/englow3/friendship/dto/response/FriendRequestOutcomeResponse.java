package com.englow3.friendship.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.friendship.dto.result.FriendRequestResult;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code status} is {@code PENDING} or {@code ACCEPTED}. */
public record FriendRequestOutcomeResponse(UUID requestId, UUID userId, String status, Instant requestedAt,
        @Schema(nullable = true) Instant acceptedAt) {

    public static FriendRequestOutcomeResponse from(FriendRequestResult result) {
        return new FriendRequestOutcomeResponse(result.requestId(), result.userId(), result.status().name(),
                result.requestedAt(), result.acceptedAt());
    }
}
