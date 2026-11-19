package com.englow3.blog.dto.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.blog.entity.BlogModerationActionType;
import com.englow3.blog.entity.BlogReportReason;
import com.englow3.blog.entity.BlogReportStatus;
import com.englow3.blog.entity.BlogTargetType;

/**
 * A reported post or comment as a moderator sees it, in any status, with every report made about it and everything
 * moderators have done to it. {@code targetStatus} is the content's own status, or {@code DELETED} when the author has
 * deleted it: then {@code content} and {@code author} are null and the reports and actions remain.
 */
public record ModerationTargetResult(BlogTargetType targetType, UUID targetId, String targetStatus, Content content,
        BlogAuthorResult author, List<Report> reports, List<Action> actions) {

    /**
     * For a comment, {@code title} and {@code postId} are those of the post it is on, and {@code images} and
     * {@code tags} are empty; for a post {@code postId} is null.
     */
    public record Content(String title, String body, List<BlogImageResult> images, List<BlogTagResult> tags,
            UUID postId, Instant createdAt) {
    }

    /** {@code reporter} is null if no profile is found for them. */
    public record Report(UUID id, BlogAuthorResult reporter, BlogReportReason reason, String detail,
            BlogReportStatus status, String resolutionNote, Instant createdAt, Instant resolvedAt) {
    }

    public record Action(UUID id, BlogModerationActionType action, String reason, BlogAuthorResult moderator,
            Instant createdAt) {
    }
}
