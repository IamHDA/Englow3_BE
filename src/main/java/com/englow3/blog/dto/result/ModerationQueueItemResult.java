package com.englow3.blog.dto.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.blog.entity.BlogReportReason;
import com.englow3.blog.entity.BlogTargetType;

/**
 * One piece of reported content, however many reports it has. {@code title} is the post's, or for a comment the title
 * of the post it is on. {@code targetStatus} is the post's or the comment's status, or {@code DELETED} when the author
 * has deleted it since: then {@code title}, {@code excerpt} and {@code author} are null.
 */
public record ModerationQueueItemResult(BlogTargetType targetType, UUID targetId, String title, String excerpt,
        BlogAuthorResult author, String targetStatus, long openReports, List<BlogReportReason> reasons,
        Instant firstReportedAt, Instant lastReportedAt) {
}
