package com.englow3.blog.dto.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.blog.entity.BlogPostStatus;

/**
 * One post in a list. {@code author} is null once the author's account is erased. {@code status} is PUBLISHED except
 * when an author lists their own posts, which includes the hidden and removed ones.
 */
public record BlogPostSummaryResult(UUID id, String title, String excerpt, BlogAuthorResult author,
        BlogPostStatus status, List<BlogTagResult> tags, long likeCount, long commentCount, boolean likedByMe,
        Instant createdAt, Instant editedAt) {
}
