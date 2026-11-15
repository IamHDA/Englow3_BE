package com.englow3.friendship.dto.result;

import java.util.UUID;

import com.englow3.friendship.entity.RelationshipStatus;

/** {@code requestId} is set only when a request is waiting for the caller to answer. */
public record LearnerSearchResult(UUID userId, String displayName, String avatarUrl, RelationshipStatus relationship,
        UUID requestId) {
}
