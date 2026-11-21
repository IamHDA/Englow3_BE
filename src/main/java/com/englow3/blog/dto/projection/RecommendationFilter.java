package com.englow3.blog.dto.projection;

import java.util.Set;
import java.util.UUID;

/**
 * Whose recommendations are being made, for {@code BlogRecommendationQuery}: posts that are published and that
 * {@code viewerId} may read, did not write and has not saved. {@code hiddenAuthors} are the authors the viewer and they
 * hide from each other; it may be empty, and never holds null.
 */
public record RecommendationFilter(UUID viewerId, Set<UUID> hiddenAuthors) {
}
