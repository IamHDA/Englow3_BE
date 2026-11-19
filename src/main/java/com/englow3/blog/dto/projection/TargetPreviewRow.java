package com.englow3.blog.dto.projection;

import java.time.Instant;
import java.util.UUID;

import com.englow3.blog.entity.BlogTargetType;

/**
 * The start of a reported post or comment. {@code title} is the post's own, or the title of the post a comment is on;
 * {@code postId} is null for a post. {@code authorId} is null when the author's account is erased. {@code status} is
 * the post's or the comment's. {@code bodyHead} is the first 400 characters of the text, which can end in the middle of
 * Markdown syntax.
 */
public record TargetPreviewRow(BlogTargetType targetType, UUID id, UUID authorId, String title, String bodyHead,
        String status, Instant createdAt, UUID postId) {
}
