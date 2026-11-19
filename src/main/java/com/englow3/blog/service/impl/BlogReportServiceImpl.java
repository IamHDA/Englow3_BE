package com.englow3.blog.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.blog.dto.command.ReportContentCommand;
import com.englow3.blog.dto.result.BlogReportResult;
import com.englow3.blog.dto.result.ReportOutcome;
import com.englow3.blog.entity.BlogComment;
import com.englow3.blog.entity.BlogCommentStatus;
import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.entity.BlogReport;
import com.englow3.blog.entity.BlogReportStatus;
import com.englow3.blog.entity.BlogTargetType;
import com.englow3.blog.repository.BlogCommentRepository;
import com.englow3.blog.repository.BlogPostRepository;
import com.englow3.blog.repository.BlogReportRepository;
import com.englow3.blog.service.BlogReportService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * Reports under the blog's read rule: a reader reports only what they can read. The rules about a report live in
 * {@link BlogReport}; what is here is the order things are asked in, and the one statement that decides whether this is
 * a new report or the reader's open one.
 */
@Service
@RequiredArgsConstructor
public class BlogReportServiceImpl implements BlogReportService {

    /** A reader files a few reports in an hour at most; a script or a grudge does not stop at ten. */
    static final int MAX_REPORTS_PER_WINDOW = 10;
    static final Duration REPORT_WINDOW = Duration.ofHours(1);

    private final UserDirectory userDirectory;
    private final BlogReadRule readRule;
    private final BlogPostRepository postRepo;
    private final BlogCommentRepository commentRepo;
    private final BlogReportRepository reportRepo;
    private final UserRateLimiter rateLimiter;
    private final Clock clock;

    @Override
    @Transactional
    public ReportOutcome reportPost(UUID postId, ReportContentCommand command) {
        UUID me = userDirectory.requireCurrentUserId();
        rateLimiter.check(me, "blog-report", MAX_REPORTS_PER_WINDOW, REPORT_WINDOW);

        BlogPost post = postRepo.findById(postId).filter(found -> readRule.canRead(found, me)).orElseThrow(
                () -> new NotFoundException("BLOG_POST_NOT_FOUND", "No post with id %s".formatted(postId)));
        if (post.isAuthor(me)) {
            throw new BadRequestException("BLOG_REPORT_OWN_CONTENT", "You cannot report your own post");
        }
        post.ensureReportable();
        return file(me, BlogTargetType.POST, postId, command);
    }

    @Override
    @Transactional
    public ReportOutcome reportComment(UUID commentId, ReportContentCommand command) {
        UUID me = userDirectory.requireCurrentUserId();
        rateLimiter.check(me, "blog-report", MAX_REPORTS_PER_WINDOW, REPORT_WINDOW);

        // Everything that makes the comment unreadable to this reader is the one answer: there is no such comment.
        BlogComment comment = commentRepo.findById(commentId)
                .filter(found -> found.getStatus() == BlogCommentStatus.VISIBLE).filter(found -> canRead(found, me))
                .orElseThrow(() -> new NotFoundException("BLOG_COMMENT_NOT_FOUND",
                        "No comment with id %s".formatted(commentId)));
        if (comment.isAuthor(me)) {
            throw new BadRequestException("BLOG_REPORT_OWN_CONTENT", "You cannot report your own comment");
        }
        return file(me, BlogTargetType.COMMENT, commentId, command);
    }

    /** The post it is on is readable by the reader, and the comment's author has no block with them. */
    private boolean canRead(BlogComment comment, UUID me) {
        return postRepo.findById(comment.getPostId()).filter(post -> readRule.canRead(post, me)).isPresent()
                && !readRule.isBlockedWith(comment.getAuthorId(), me);
    }

    /**
     * The entity checks the note and makes the report; the insert then decides, atomically, against the index that
     * allows one open report per reader and target: 1 row is a new report, 0 is the reader's open one, which is read
     * back. Two requests at once, or a retry, cannot make two.
     */
    private ReportOutcome file(UUID me, BlogTargetType type, UUID targetId, ReportContentCommand command) {
        Instant now = Instant.now(clock);
        BlogReport report = BlogReport.open(me, type, targetId, command.reason(), command.detail(), now);
        int inserted = reportRepo.insertIfAbsent(report.getId(), me, type.name(), targetId, report.getReason().name(),
                report.getDetail(), now);
        BlogReport open = reportRepo
                .findByReporterIdAndTargetTypeAndTargetIdAndStatus(me, type, targetId, BlogReportStatus.OPEN)
                // The reader's earlier report was closed between the insert finding it and this read: nothing is
                // wrong, and asking again files a new one.
                .orElseThrow(() -> new ConflictException("BLOG_REPORT_CLOSED",
                        "The report you had open was closed a moment ago; report again"));
        return new ReportOutcome(result(open), inserted == 1);
    }

    private static BlogReportResult result(BlogReport report) {
        return new BlogReportResult(report.getId(), report.getTargetType(), report.getTargetId(), report.getReason(),
                report.getDetail(), report.getStatus(), report.getCreatedAt());
    }
}
