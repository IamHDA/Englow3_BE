package com.englow3.blog.dto.projection;

import java.util.Set;
import java.util.UUID;

import com.englow3.blog.entity.BlogCertificate;
import com.englow3.blog.entity.BlogLevel;
import com.englow3.blog.entity.BlogPostSort;
import com.englow3.blog.entity.BlogSkill;

/**
 * Everything the post list asks of {@code BlogPostListQuery}, built by the service. Every field but {@code viewerId},
 * {@code hiddenAuthors}, {@code sort} and the paging may be null, and a null filter is not applied.
 * {@code hiddenAuthors} are the authors the viewer and they hide from each other; it may be empty, and never holds
 * null. {@code titleTerm} is the text as typed, trimmed: the query makes it a pattern.
 */
public record PostListFilter(UUID viewerId, Set<UUID> hiddenAuthors, String titleTerm, BlogSkill skill, BlogLevel level,
        BlogCertificate certificate, UUID authorId, BlogPostSort sort, int limit, int offset) {

    /**
     * Whether hidden and removed posts are listed too. They are for exactly one question: an author asking for their
     * own posts, which is how they find the ones a moderator took down. For everyone else, and for everything else the
     * author asks, the list holds published posts only.
     */
    public boolean includesEveryStatus() {
        return authorId != null && authorId.equals(viewerId);
    }
}
