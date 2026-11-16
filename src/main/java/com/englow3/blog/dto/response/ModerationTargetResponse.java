package com.englow3.blog.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.blog.dto.result.ModerationTargetResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A reported post or comment as a moderator sees it, in any status, with every report about it and the history of
 * moderation. {@code targetStatus} is the content's own status, or DELETED when the author has deleted it: then
 * {@code content} and {@code author} are null and the reports and actions remain.
 */
public record ModerationTargetResponse(String targetType, UUID targetId, String targetStatus,
        @Schema(nullable = true) Content content, @Schema(nullable = true) BlogAuthorResponse author,
        List<Report> reports, List<Action> actions) {

    /**
     * For a comment, {@code title} and {@code postId} are those of the post it is on, and {@code images} and
     * {@code tags} are empty. For a post {@code postId} is null.
     */
    public record Content(@Schema(nullable = true) String title, String body, List<BlogImageResponse> images,
            List<BlogTagResponse> tags, @Schema(nullable = true) UUID postId, Instant createdAt) {
    }

    public record Report(UUID id, @Schema(nullable = true) BlogAuthorResponse reporter, String reason,
            @Schema(nullable = true) String detail, String status, @Schema(nullable = true) String resolutionNote,
            Instant createdAt, @Schema(nullable = true) Instant resolvedAt) {
    }

    public record Action(UUID id, String action, String reason, @Schema(nullable = true) BlogAuthorResponse moderator,
            Instant createdAt) {
    }

    public static ModerationTargetResponse from(ModerationTargetResult result) {
        Content content = result.content() == null ? null
                : new Content(result.content().title(), result.content().body(),
                        result.content().images().stream().map(BlogImageResponse::from).toList(),
                        result.content().tags().stream().map(BlogTagResponse::from).toList(), result.content().postId(),
                        result.content().createdAt());
        List<Report> reports = result.reports().stream()
                .map(report -> new Report(report.id(), BlogAuthorResponse.from(report.reporter()),
                        report.reason().name(), report.detail(), report.status().name(), report.resolutionNote(),
                        report.createdAt(), report.resolvedAt()))
                .toList();
        List<Action> actions = result.actions().stream().map(action -> new Action(action.id(), action.action().name(),
                action.reason(), BlogAuthorResponse.from(action.moderator()), action.createdAt())).toList();
        return new ModerationTargetResponse(result.targetType().name(), result.targetId(), result.targetStatus(),
                content, BlogAuthorResponse.from(result.author()), reports, actions);
    }
}
