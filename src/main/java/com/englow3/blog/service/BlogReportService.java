package com.englow3.blog.service;

import java.util.UUID;

import com.englow3.blog.dto.command.ReportContentCommand;
import com.englow3.blog.dto.result.ReportOutcome;

/**
 * A reader reports a post or a comment they are looking at. Content the reader cannot read - missing, hidden from them,
 * by someone they have a block with - is {@code BLOG_POST_NOT_FOUND} or {@code BLOG_COMMENT_NOT_FOUND}, as everywhere
 * in the blog; one's own content cannot be reported. A reader has at most one open report for a piece of content:
 * reporting it again while the first is open returns the first ({@code created} false), and once a moderator has closed
 * it the reader may report the content again.
 */
public interface BlogReportService {

    /** A published post. A hidden or removed one needs no report: {@code BLOG_POST_NOT_OPEN}. */
    ReportOutcome reportPost(UUID postId, ReportContentCommand command);

    /** A visible comment on a post the reader can read, by someone they have no block with. */
    ReportOutcome reportComment(UUID commentId, ReportContentCommand command);
}
