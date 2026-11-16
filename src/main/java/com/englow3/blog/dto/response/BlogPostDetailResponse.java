package com.englow3.blog.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.blog.dto.result.BlogPostDetailResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code body} is the Markdown exactly as written, with {@code blog-image:<id>} where an image goes; {@code images}
 * gives each id a short-lived link to swap in. {@code version} is what an edit sends back. {@code author} is null when
 * the author has erased their account. {@code canInteract} is whether comments and likes are open.
 */
public record BlogPostDetailResponse(UUID id, String title, String body, List<BlogImageResponse> images,
        @Schema(nullable = true) BlogAuthorResponse author, List<BlogTagResponse> tags, String status, long version,
        long likeCount, long commentCount, boolean likedByMe, boolean canEdit, boolean canInteract, Instant createdAt,
        @Schema(nullable = true) Instant editedAt) {

    public static BlogPostDetailResponse from(BlogPostDetailResult result) {
        return new BlogPostDetailResponse(result.id(), result.title(), result.body(),
                result.images().stream().map(BlogImageResponse::from).toList(),
                BlogAuthorResponse.from(result.author()), result.tags().stream().map(BlogTagResponse::from).toList(),
                result.status().name(), result.version(), result.likeCount(), result.commentCount(), result.likedByMe(),
                result.canEdit(), result.canInteract(), result.createdAt(), result.editedAt());
    }
}
