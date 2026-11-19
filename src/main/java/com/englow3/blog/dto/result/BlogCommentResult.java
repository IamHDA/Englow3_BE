package com.englow3.blog.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.blog.entity.BlogCommentStatus;

/** {@code status} is VISIBLE except for the viewer's own hidden or removed comments. */
public record BlogCommentResult(UUID id, UUID postId, BlogAuthorResult author, String body, BlogCommentStatus status,
        boolean canEdit, Instant createdAt, Instant editedAt) {
}
