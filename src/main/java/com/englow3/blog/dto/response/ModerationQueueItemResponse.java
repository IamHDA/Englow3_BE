package com.englow3.blog.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.blog.dto.result.ModerationQueueItemResult;
import com.englow3.blog.entity.BlogReportReason;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One piece of reported content, however many reports it has. {@code title} is the post's, or for a comment the title
 * of the post it is on. {@code targetStatus} is PUBLISHED, HIDDEN or REMOVED for a post, VISIBLE, HIDDEN or REMOVED for
 * a comment, or DELETED when the author has deleted it: then {@code title}, {@code excerpt} and {@code author} are
 * null.
 */
public record ModerationQueueItemResponse(String targetType, UUID targetId, @Schema(nullable = true) String title,
        @Schema(nullable = true) String excerpt, @Schema(nullable = true) BlogAuthorResponse author,
        String targetStatus, long openReports, List<String> reasons, Instant firstReportedAt, Instant lastReportedAt) {

    public static ModerationQueueItemResponse from(ModerationQueueItemResult result) {
        return new ModerationQueueItemResponse(result.targetType().name(), result.targetId(), result.title(),
                result.excerpt(), BlogAuthorResponse.from(result.author()), result.targetStatus(), result.openReports(),
                result.reasons().stream().map(BlogReportReason::name).toList(), result.firstReportedAt(),
                result.lastReportedAt());
    }
}
