package com.englow3.blog.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.blog.dto.command.ModerationActionCommand;
import com.englow3.blog.dto.command.ModerationQueueFilter;
import com.englow3.blog.dto.result.ModerationActionResult;
import com.englow3.blog.dto.result.ModerationQueueItemResult;
import com.englow3.blog.dto.result.ModerationTargetResult;
import com.englow3.blog.entity.BlogTargetType;

/**
 * What Staff and Admin do with reported posts and comments. Reached only through {@code /api/admin/blog}, which is
 * closed to anyone else, so none of this applies the reader's read rule: a moderator sees content in every status. A
 * moderator never acts on content they wrote themselves.
 */
public interface BlogModerationService {

    /**
     * The content that has open reports, one row each however many reports it has: oldest first or most reported first,
     * optionally only posts or only comments.
     */
    Page<ModerationQueueItemResult> queue(ModerationQueueFilter filter, Pageable page);

    /**
     * One post or comment in full, in any status, with every report made about it and everything moderators have done
     * to it. Content its author has deleted is still described by its reports and history: {@code content} is null and
     * {@code targetStatus} is {@code DELETED}. Nothing at all to show is {@code BLOG_TARGET_NOT_FOUND}.
     */
    ModerationTargetResult target(BlogTargetType type, UUID targetId);

    /**
     * Hides (reversible), restores, removes (permanent) or dismisses the reports of a post or comment, with a reason,
     * and records it. Hiding and removing close every open report of the content as resolved, dismissing closes them as
     * dismissed and leaves the content alone, restoring leaves the reports as they are. Refused for content the
     * moderator wrote ({@code BLOG_MODERATION_OWN_CONTENT}) before anything is locked or written. Returns the content
     * as it is afterwards.
     */
    ModerationTargetResult act(BlogTargetType type, UUID targetId, ModerationActionCommand command);

    /** The history of everything moderators did, newest first. */
    Page<ModerationActionResult> recentActions(Pageable page);
}
