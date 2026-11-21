package com.englow3.blog.dto.projection;

import java.time.Instant;
import java.util.UUID;

/** A post that may be recommended: what it takes to rank it, apart from its tags. */
public record RecommendationCandidate(UUID postId, Instant createdAt, long likeCount) {
}
