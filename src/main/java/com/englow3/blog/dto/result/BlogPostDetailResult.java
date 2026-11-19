package com.englow3.blog.dto.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.blog.entity.BlogPostStatus;

/**
 * One post in full. {@code body} is the Markdown as written, with {@code blog-image:<id>} references that
 * {@code images} resolves to links. {@code version} is what an edit must send back. {@code moderationNote} is the
 * reason a moderator gave when they hid or removed the post; it is set only for the author, and only while the post is
 * not published.
 */
public record BlogPostDetailResult(UUID id, String title, String body, List<BlogImageResult> images,
        BlogAuthorResult author, List<BlogTagResult> tags, BlogPostStatus status, long version, long likeCount,
        long commentCount, boolean likedByMe, boolean canEdit, boolean canInteract, Instant createdAt, Instant editedAt,
        String moderationNote) {
}
