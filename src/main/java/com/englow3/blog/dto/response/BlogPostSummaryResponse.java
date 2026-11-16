package com.englow3.blog.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.blog.dto.result.BlogPostSummaryResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * {@code author} is null when the author has erased their account. {@code status} is always PUBLISHED, except when an
 * author lists their own posts: then hidden and removed ones are in the list, marked.
 */
public record BlogPostSummaryResponse(UUID id, String title, String excerpt,
        @Schema(nullable = true) BlogAuthorResponse author, String status, List<BlogTagResponse> tags, long likeCount,
        long commentCount, boolean likedByMe, Instant createdAt, @Schema(nullable = true) Instant editedAt) {

    public static BlogPostSummaryResponse from(BlogPostSummaryResult result) {
        return new BlogPostSummaryResponse(result.id(), result.title(), result.excerpt(),
                BlogAuthorResponse.from(result.author()), result.status().name(),
                result.tags().stream().map(BlogTagResponse::from).toList(), result.likeCount(), result.commentCount(),
                result.likedByMe(), result.createdAt(), result.editedAt());
    }
}
