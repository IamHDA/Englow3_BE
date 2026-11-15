package com.englow3.friendship.dto.result;

import java.time.Instant;
import java.util.UUID;

/** A friend of the caller. {@code userId} is the other person, never the caller. */
public record FriendResult(UUID userId, String displayName, String avatarUrl, Instant friendsSince) {
}
