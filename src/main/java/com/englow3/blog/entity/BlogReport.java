package com.englow3.blog.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * A reader's report of a post or a comment. It is closed once, by a moderator, as RESOLVED (they acted on the content)
 * or DISMISSED (they did not), and never reopened: a reader who still has a complaint after that makes a new report.
 * {@code targetId} names a post or a comment according to {@code targetType}, so the database has no foreign key to it.
 * <p>
 * A moderator's action closes every open report of the content at once with one {@code update} statement (see the
 * repository), which cannot go through this class; {@link #resolve} and {@link #dismiss} keep the rule for closing one
 * report and must leave the same row behind.
 * <p>
 * The 500-character limit on the detail is also a column limit; here it gives the caller an error with a code.
 */
@Entity
@Table(name = "blog_reports")
@Getter
public class BlogReport extends BasePersistedEntity {

    static final int MAX_DETAIL_LENGTH = 500;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 20)
    private BlogTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private BlogReportReason reason;

    @Column(updatable = false, length = MAX_DETAIL_LENGTH)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlogReportStatus status;

    @Column(name = "resolved_by_user_id")
    private UUID resolvedByUserId;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BlogReport() {
    }

    /**
     * A new open report. The detail is kept stripped of the spaces around it, and a blank one is no detail at all.
     */
    public static BlogReport open(UUID reporterId, BlogTargetType type, UUID targetId, BlogReportReason reason,
            String detail, Instant now) {
        BlogReport report = new BlogReport();
        report.id = UUID.randomUUID();
        report.reporterId = reporterId;
        report.targetType = type;
        report.targetId = targetId;
        report.reason = reason;
        report.detail = validDetail(detail);
        report.status = BlogReportStatus.OPEN;
        report.createdAt = now;
        return report;
    }

    /** The moderator acted on the content this report is about. Only an open report can be closed. */
    public void resolve(UUID moderatorId, String note, Instant now) {
        close(BlogReportStatus.RESOLVED, moderatorId, note, now);
    }

    /** The moderator looked and left the content as it was. Only an open report can be closed. */
    public void dismiss(UUID moderatorId, String note, Instant now) {
        close(BlogReportStatus.DISMISSED, moderatorId, note, now);
    }

    public boolean isOpen() {
        return status == BlogReportStatus.OPEN;
    }

    private void close(BlogReportStatus closedAs, UUID moderatorId, String note, Instant now) {
        if (status != BlogReportStatus.OPEN) {
            throw new ConflictException("BLOG_REPORT_CLOSED", "This report has already been closed");
        }
        this.status = closedAs;
        this.resolvedByUserId = moderatorId;
        this.resolutionNote = note;
        this.resolvedAt = now;
    }

    private static String validDetail(String detail) {
        String stripped = detail == null ? "" : detail.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        if (stripped.length() > MAX_DETAIL_LENGTH) {
            throw new BadRequestException("BLOG_REPORT_DETAIL_TOO_LONG",
                    "A note may have at most %d characters".formatted(MAX_DETAIL_LENGTH));
        }
        return stripped;
    }
}
