package com.englow3.friendship.dto.result;

import java.time.Instant;
import java.util.UUID;

public record BlockedUserResult(UUID userId, String displayName, String avatarUrl, Instant blockedAt) {
}
