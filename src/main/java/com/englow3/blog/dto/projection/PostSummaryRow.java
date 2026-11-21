package com.englow3.blog.dto.projection;

import java.time.Instant;
import java.util.UUID;

import com.englow3.blog.entity.BlogPostStatus;

/**
 * One row of the post list. {@code authorId} is null when the author's account is erased. {@code bodyHead} is the first
 * 1200 characters of the text, which can end in the middle of Markdown syntax; the excerpt is made from it afterwards.
 */
public record PostSummaryRow(UUID id, UUID authorId, String title, BlogPostStatus status, String bodyHead,
        Instant createdAt, Instant editedAt, long likeCount, long commentCount, boolean likedByMe, boolean savedByMe) {
}
