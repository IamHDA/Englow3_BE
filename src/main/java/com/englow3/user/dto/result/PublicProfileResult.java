package com.englow3.user.dto.result;

import java.time.Instant;
import java.util.UUID;

/** What anyone signed in may see of an account. URLs are final; nothing here is private to the owner. */
public record PublicProfileResult(UUID userId, String displayName, String avatarUrl, String bannerUrl, boolean staff,
        Instant joinedAt) {
}
