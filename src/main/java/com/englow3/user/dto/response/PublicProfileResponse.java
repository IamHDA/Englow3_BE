package com.englow3.user.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.user.dto.result.PublicProfileResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The basics shown on an account's wall. {@code staff} is true for a STAFF or ADMIN account, which has a wall but no
 * friend actions. Deliberately nothing about level, goals or scores.
 */
public record PublicProfileResponse(UUID userId, String displayName, @Schema(nullable = true) String avatarUrl,
        @Schema(nullable = true) String bannerUrl, boolean staff, Instant joinedAt) {

    public static PublicProfileResponse from(PublicProfileResult result) {
        return new PublicProfileResponse(result.userId(), result.displayName(), result.avatarUrl(), result.bannerUrl(),
                result.staff(), result.joinedAt());
    }
}
