package com.englow3.friendship.dto.response;

import java.util.UUID;

import com.englow3.friendship.dto.result.RelationshipResult;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code relationship} is a RelationshipStatus name. */
public record RelationshipResponse(UUID userId, String relationship, @Schema(nullable = true) UUID requestId) {

    public static RelationshipResponse from(RelationshipResult result) {
        return new RelationshipResponse(result.userId(), result.relationship().name(), result.requestId());
    }
}
