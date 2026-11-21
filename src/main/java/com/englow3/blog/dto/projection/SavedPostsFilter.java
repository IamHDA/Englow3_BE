package com.englow3.blog.dto.projection;

import java.util.Set;
import java.util.UUID;

/**
 * What the saved list asks of {@code BlogPostListQuery}: the posts {@code viewerId} saved, latest save first, that are
 * still published and not by anyone in {@code hiddenAuthors} - the authors the viewer and they hide from each other,
 * possibly empty, never holding null.
 */
public record SavedPostsFilter(UUID viewerId, Set<UUID> hiddenAuthors, int limit, int offset) {
}
