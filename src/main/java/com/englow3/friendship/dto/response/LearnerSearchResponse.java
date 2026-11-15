package com.englow3.friendship.dto.response;

import java.util.UUID;

import com.englow3.friendship.dto.result.LearnerSearchResult;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code relationship} is a RelationshipStatus name. */
public record LearnerSearchResponse(UUID userId, String displayName, @Schema(nullable = true) String avatarUrl,
        String relationship, @Schema(nullable = true) UUID requestId) {

    public static LearnerSearchResponse from(LearnerSearchResult result) {
        return new LearnerSearchResponse(result.userId(), result.displayName(), result.avatarUrl(),
                result.relationship().name(), result.requestId());
    }
}
