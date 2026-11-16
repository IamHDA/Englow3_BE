package com.englow3.blog.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.blog.dto.result.BlogCommentResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code author} is null when the author has erased their account. {@code status} is VISIBLE, except for a viewer's own
 * comments that were hidden or removed, which only they see.
 */
public record BlogCommentResponse(UUID id, UUID postId, @Schema(nullable = true) BlogAuthorResponse author, String body,
        String status, boolean canEdit, Instant createdAt, @Schema(nullable = true) Instant editedAt) {

    public static BlogCommentResponse from(BlogCommentResult result) {
        return new BlogCommentResponse(result.id(), result.postId(), BlogAuthorResponse.from(result.author()),
                result.body(), result.status().name(), result.canEdit(), result.createdAt(), result.editedAt());
    }
}
