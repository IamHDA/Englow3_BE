package com.englow3.friendship.dto.result;

import java.util.UUID;

import com.englow3.friendship.entity.RelationshipStatus;

public record RelationshipResult(UUID userId, RelationshipStatus relationship, UUID requestId) {
}
