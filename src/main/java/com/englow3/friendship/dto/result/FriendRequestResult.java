package com.englow3.friendship.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.friendship.entity.FriendshipStatus;

/**
 * One request seen from the caller's side. {@code userId} is the other person, never the caller. {@code acceptedAt} is
 * set only once the request has been accepted, which is when a send or an accept hands one back.
 */
public record FriendRequestResult(UUID requestId, UUID userId, String displayName, String avatarUrl,
        FriendshipStatus status, Instant requestedAt, Instant acceptedAt) {
}
