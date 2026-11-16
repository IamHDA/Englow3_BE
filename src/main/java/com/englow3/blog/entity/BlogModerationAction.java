package com.englow3.blog.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.persistence.BasePersistedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One thing a moderator did to a post or a comment, with the reason they gave: the history that stays after the
 * content, or the reporter's account, is gone. Only ever added - there is no way to change a row once it is recorded -
 * and no foreign key to the target, which may be deleted by its author. {@code reportId} is the most recent open report
 * the action closed, if there was one.
 */
@Entity
@Table(name = "blog_moderation_actions")
@Getter
public class BlogModerationAction extends BasePersistedEntity {

    static final int MAX_REASON_LENGTH = 500;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, updatable = false, length = 20)
    private BlogTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private BlogModerationActionType action;

    @Column(nullable = false, updatable = false, length = MAX_REASON_LENGTH)
    private String reason;

    @Column(name = "moderator_id", nullable = false, updatable = false)
    private UUID moderatorId;

    @Column(name = "report_id", updatable = false)
    private UUID reportId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BlogModerationAction() {
    }

    /** The reason is kept stripped of the spaces around it. {@code reportId} is null when there was no open report. */
    public static BlogModerationAction record(BlogTargetType type, UUID targetId, BlogModerationActionType action,
            String reason, UUID moderatorId, UUID reportId, Instant now) {
        BlogModerationAction recorded = new BlogModerationAction();
        recorded.id = UUID.randomUUID();
        recorded.targetType = type;
        recorded.targetId = targetId;
        recorded.action = action;
        recorded.reason = validReason(reason);
        recorded.moderatorId = moderatorId;
        recorded.reportId = reportId;
        recorded.createdAt = now;
        return recorded;
    }

    /**
     * The reason as it will be kept: stripped, 1 to {@value #MAX_REASON_LENGTH} characters. Public so that a service
     * can refuse a bad one before it locks or writes anything, with the same rule {@link #record} applies.
     */
    public static String validReason(String reason) {
        String stripped = reason == null ? "" : reason.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException("BLOG_MODERATION_REASON_INVALID",
                    "A reason needs 1 to %d characters".formatted(MAX_REASON_LENGTH));
        }
        return stripped;
    }
}
