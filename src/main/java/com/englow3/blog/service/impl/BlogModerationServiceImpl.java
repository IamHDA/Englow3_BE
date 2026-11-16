package com.englow3.blog.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.blog.dto.command.ModerationActionCommand;
import com.englow3.blog.dto.command.ModerationQueueFilter;
import com.englow3.blog.dto.projection.QueueRow;
import com.englow3.blog.dto.projection.TargetPreviewRow;
import com.englow3.blog.dto.result.BlogAuthorResult;
import com.englow3.blog.dto.result.BlogTagResult;
import com.englow3.blog.dto.result.ModerationActionResult;
import com.englow3.blog.dto.result.ModerationQueueItemResult;
import com.englow3.blog.dto.result.ModerationTargetResult;
import com.englow3.blog.entity.BlogComment;
import com.englow3.blog.entity.BlogModerationAction;
import com.englow3.blog.entity.BlogModerationActionType;
import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.entity.BlogQueueSort;
import com.englow3.blog.entity.BlogReport;
import com.englow3.blog.entity.BlogReportStatus;
import com.englow3.blog.entity.BlogTargetType;
import com.englow3.blog.helper.BlogMarkdown;
import com.englow3.blog.query.BlogModerationQueueQuery;
import com.englow3.blog.repository.BlogCommentRepository;
import com.englow3.blog.repository.BlogModerationActionRepository;
import com.englow3.blog.repository.BlogPostRepository;
import com.englow3.blog.repository.BlogReportRepository;
import com.englow3.blog.service.BlogModerationService;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * What Staff and Admin do with reported content, in the order that keeps it safe: a bad reason and a moderator's own
 * content are refused before anything is locked or written; the content is then locked and changed by the entity, which
 * refuses a change that makes no sense; the action is recorded; the open reports are closed in one statement. The rules
 * about a post, a comment, a report and an action live in their entities; what is here is the order, and the decision
 * of which reports an action closes.
 * <p>
 * Everything here runs behind {@code /api/admin/blog}, so the reader's read rule is deliberately not applied: a
 * moderator sees content in every status.
 */
@Service
@RequiredArgsConstructor
public class BlogModerationServiceImpl implements BlogModerationService {

    static final int MAX_PAGE_SIZE = 50;

    /** What a comment's preview in the queue is cut to, the ellipsis included. */
    static final int COMMENT_EXCERPT_LENGTH = 200;

    private static final String ELLIPSIS = "…";
    private static final String DELETED = "DELETED";

    private final UserDirectory userDirectory;
    private final PublicProfileReader publicProfileReader;
    private final BlogPostRepository postRepo;
    private final BlogCommentRepository commentRepo;
    private final BlogReportRepository reportRepo;
    private final BlogModerationActionRepository actionRepo;
    private final BlogModerationQueueQuery queueQuery;
    private final BlogImageLinks imageLinks;
    private final Clock clock;

    private record TargetKey(BlogTargetType type, UUID id) {
    }

    /** The content as it is now: its status, who wrote it (null once erased) and what it says. */
    private record Loaded(String status, UUID authorId, ModerationTargetResult.Content content) {
    }

    // --- queue -----------------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<ModerationQueueItemResult> queue(ModerationQueueFilter filter, Pageable page) {
        Pageable pageable = PageRequest.of(page.getPageNumber(), pageSize(page));
        // An offset past what an int can hold is past any queue: the empty page it gets is the right answer.
        int offset = (int) Math.min(pageable.getOffset(), Integer.MAX_VALUE);
        BlogQueueSort sort = filter.sort() == null ? BlogQueueSort.OLDEST : filter.sort();

        List<QueueRow> rows = queueQuery.queue(filter.type(), sort, pageable.getPageSize(), offset);
        Map<TargetKey, TargetPreviewRow> previews = previewsOf(rows);
        // One call for the whole page. A null author is an erased account, and content already deleted has no preview.
        Set<UUID> authorIds = previews.values().stream().map(TargetPreviewRow::authorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, PublicProfile> profiles = profilesOf(authorIds);

        List<ModerationQueueItemResult> content = rows.stream()
                .map(row -> queueItem(row, previews.get(new TargetKey(row.targetType(), row.targetId())), profiles))
                .toList();
        return PageableExecutionUtils.getPage(content, pageable, () -> queueQuery.count(filter.type()));
    }

    private Map<TargetKey, TargetPreviewRow> previewsOf(List<QueueRow> rows) {
        Set<UUID> postIds = idsOf(rows, BlogTargetType.POST);
        Set<UUID> commentIds = idsOf(rows, BlogTargetType.COMMENT);
        return queueQuery.previews(postIds, commentIds).stream()
                .collect(Collectors.toMap(row -> new TargetKey(row.targetType(), row.id()), Function.identity()));
    }

    private static Set<UUID> idsOf(List<QueueRow> rows, BlogTargetType type) {
        return rows.stream().filter(row -> row.targetType() == type).map(QueueRow::targetId)
                .collect(Collectors.toSet());
    }

    private static ModerationQueueItemResult queueItem(QueueRow row, TargetPreviewRow preview,
            Map<UUID, PublicProfile> profiles) {
        if (preview == null) {
            // The author deleted it after it was reported; the open reports stay until someone dismisses them.
            return new ModerationQueueItemResult(row.targetType(), row.targetId(), null, null, null, DELETED,
                    row.openReports(), row.reasons(), row.firstReportedAt(), row.lastReportedAt());
        }
        String excerpt = row.targetType() == BlogTargetType.POST ? BlogMarkdown.excerpt(preview.bodyHead())
                : commentExcerpt(preview.bodyHead());
        return new ModerationQueueItemResult(row.targetType(), row.targetId(), preview.title(), excerpt,
                authorOf(preview.authorId(), profiles), preview.status(), row.openReports(), row.reasons(),
                row.firstReportedAt(), row.lastReportedAt());
    }

    /**
     * A comment is plain text: cut at {@value #COMMENT_EXCERPT_LENGTH} characters, not in the middle of a character.
     */
    static String commentExcerpt(String body) {
        String text = body.strip();
        if (text.length() <= COMMENT_EXCERPT_LENGTH) {
            return text;
        }
        int end = COMMENT_EXCERPT_LENGTH - ELLIPSIS.length();
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end).stripTrailing() + ELLIPSIS;
    }

    // --- one piece of content --------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public ModerationTargetResult target(BlogTargetType type, UUID targetId) {
        return describe(type, targetId);
    }

    /**
     * Loaded inside the caller's transaction: the response needs nothing that is lazy. Content that is gone is still
     * described by what remains about it; nothing at all is not found.
     */
    private ModerationTargetResult describe(BlogTargetType type, UUID targetId) {
        Optional<Loaded> loaded = load(type, targetId);
        List<BlogReport> reports = reportRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(type, targetId);
        List<BlogModerationAction> actions = actionRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(type, targetId);
        if (loaded.isEmpty() && reports.isEmpty() && actions.isEmpty()) {
            throw targetNotFound(type, targetId);
        }

        Set<UUID> personIds = new HashSet<>();
        loaded.map(Loaded::authorId).ifPresent(personIds::add);
        reports.forEach(report -> personIds.add(report.getReporterId()));
        actions.forEach(action -> personIds.add(action.getModeratorId()));
        personIds.remove(null);
        Map<UUID, PublicProfile> profiles = profilesOf(personIds);

        List<ModerationTargetResult.Report> reportResults = reports.stream()
                .map(report -> new ModerationTargetResult.Report(report.getId(),
                        authorOf(report.getReporterId(), profiles), report.getReason(), report.getDetail(),
                        report.getStatus(), report.getResolutionNote(), report.getCreatedAt(), report.getResolvedAt()))
                .toList();
        List<ModerationTargetResult.Action> actionResults = actions
                .stream().map(action -> new ModerationTargetResult.Action(action.getId(), action.getAction(),
                        action.getReason(), authorOf(action.getModeratorId(), profiles), action.getCreatedAt()))
                .toList();
        return new ModerationTargetResult(type, targetId, loaded.map(Loaded::status).orElse(DELETED),
                loaded.map(Loaded::content).orElse(null),
                loaded.map(found -> authorOf(found.authorId(), profiles)).orElse(null), reportResults, actionResults);
    }

    private Optional<Loaded> load(BlogTargetType type, UUID targetId) {
        return switch (type) {
            case POST -> postRepo.findWithTagsById(targetId).map(this::loaded);
            case COMMENT -> commentRepo.findById(targetId).map(this::loaded);
        };
    }

    private Loaded loaded(BlogPost post) {
        List<BlogTagResult> tags = post.getTags().stream().map(tag -> new BlogTagResult(tag.getKind(), tag.getValue()))
                .sorted(BlogTagResult.DISPLAY_ORDER).toList();
        return new Loaded(post.getStatus().name(), post.getAuthorId(),
                new ModerationTargetResult.Content(post.getTitle(), post.getBody(),
                        imageLinks.of(post.getId(), post.getBody()), tags, null, post.getCreatedAt()));
    }

    private Loaded loaded(BlogComment comment) {
        String postTitle = postRepo.findById(comment.getPostId()).map(BlogPost::getTitle).orElse(null);
        return new Loaded(comment.getStatus().name(), comment.getAuthorId(), new ModerationTargetResult.Content(
                postTitle, comment.getBody(), List.of(), List.of(), comment.getPostId(), comment.getCreatedAt()));
    }

    // --- acting ----------------------------------------------------------------------------------------------------

    @Override
    @Transactional
    public ModerationTargetResult act(BlogTargetType type, UUID targetId, ModerationActionCommand command) {
        UUID me = userDirectory.requireCurrentUserId();
        // Both refusals come before anything is locked or written, whatever the action is.
        String reason = BlogModerationAction.validReason(command.reason());
        requireNotOwnContent(type, targetId, me);

        Instant now = Instant.now(clock);
        if (command.action() == BlogModerationActionType.DISMISS) {
            dismiss(type, targetId, reason, me, now);
        } else {
            change(command.action(), type, targetId, reason, me, now);
        }
        return describe(type, targetId);
    }

    /**
     * A moderator does not decide on what they wrote: another one does. Asked about the row without loading it, so
     * nothing is held; the author never changes, so the answer cannot go stale before the lock. Content that is already
     * gone has no author to compare with.
     */
    private void requireNotOwnContent(BlogTargetType type, UUID targetId, UUID me) {
        boolean own = switch (type) {
            case POST -> postRepo.existsByIdAndAuthorId(targetId, me);
            case COMMENT -> commentRepo.existsByIdAndAuthorId(targetId, me);
        };
        if (own) {
            throw new ForbiddenException("BLOG_MODERATION_OWN_CONTENT",
                    "Another moderator has to review your own content");
        }
    }

    /**
     * Hide, restore or remove. The content is locked and read fresh, so the decision is on what it is now and an
     * author's save in flight either finishes first or finds the version changed; the entity refuses a change that
     * makes no sense. Hiding and removing close every open report of the content as resolved; restoring leaves them.
     */
    private void change(BlogModerationActionType action, BlogTargetType type, UUID targetId, String reason, UUID me,
            Instant now) {
        lockAndApply(action, type, targetId, now);
        UUID reportId = lastOpenReport(type, targetId).map(BlogReport::getId).orElse(null);
        actionRepo.save(BlogModerationAction.record(type, targetId, action, reason, me, reportId, now));
        if (action != BlogModerationActionType.RESTORE) {
            reportRepo.closeOpen(type, targetId, BlogReportStatus.RESOLVED, me, reason, now);
        }
    }

    private void lockAndApply(BlogModerationActionType action, BlogTargetType type, UUID targetId, Instant now) {
        switch (type) {
            case POST -> {
                BlogPost post = postRepo.findByIdForUpdate(targetId).orElseThrow(() -> targetNotFound(type, targetId));
                switch (action) {
                    case HIDE -> post.hide(now);
                    case RESTORE -> post.restore(now);
                    case REMOVE -> post.remove(now);
                    case DISMISS -> throw dismissIsNotAChange();
                }
            }
            case COMMENT -> {
                BlogComment comment = commentRepo.findByIdForUpdate(targetId)
                        .orElseThrow(() -> targetNotFound(type, targetId));
                switch (action) {
                    case HIDE -> comment.hide();
                    case RESTORE -> comment.restore();
                    case REMOVE -> comment.remove();
                    case DISMISS -> throw dismissIsNotAChange();
                }
            }
        }
    }

    /**
     * Closes every open report of the content as dismissed and leaves the content as it is, also when its author has
     * deleted it - which is how the queue is cleaned of it. With no open report there is nothing to dismiss, whether
     * there never was one or another moderator has just closed them.
     */
    private void dismiss(BlogTargetType type, UUID targetId, String reason, UUID me, Instant now) {
        UUID reportId = lastOpenReport(type, targetId).map(BlogReport::getId)
                .orElseThrow(BlogModerationServiceImpl::noOpenReports);
        if (reportRepo.closeOpen(type, targetId, BlogReportStatus.DISMISSED, me, reason, now) == 0) {
            throw noOpenReports();
        }
        actionRepo.save(BlogModerationAction.record(type, targetId, BlogModerationActionType.DISMISS, reason, me,
                reportId, now));
    }

    private Optional<BlogReport> lastOpenReport(BlogTargetType type, UUID targetId) {
        return reportRepo.findFirstByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(type, targetId,
                BlogReportStatus.OPEN);
    }

    // --- history ---------------------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<ModerationActionResult> recentActions(Pageable page) {
        Pageable pageable = PageRequest.of(page.getPageNumber(), pageSize(page));
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            // Past anything the database can skip over: the empty page it gets is the right answer, and the total is
            // still the real one.
            return new PageImpl<>(List.of(), pageable, actionRepo.count());
        }
        Page<BlogModerationAction> actions = actionRepo.findAllByOrderByCreatedAtDesc(pageable);
        Set<UUID> moderatorIds = actions.stream().map(BlogModerationAction::getModeratorId).collect(Collectors.toSet());
        Map<UUID, PublicProfile> profiles = profilesOf(moderatorIds);
        return actions.map(action -> new ModerationActionResult(action.getId(), action.getTargetType(),
                action.getTargetId(), action.getAction(), action.getReason(),
                authorOf(action.getModeratorId(), profiles), action.getCreatedAt()));
    }

    // --- shared ----------------------------------------------------------------------------------------------------

    private static int pageSize(Pageable page) {
        return Math.max(1, Math.min(page.getPageSize(), MAX_PAGE_SIZE));
    }

    /** One call for the whole set, and none for no one. */
    private Map<UUID, PublicProfile> profilesOf(Set<UUID> ids) {
        return ids.isEmpty() ? Map.of() : publicProfileReader.profiles(ids);
    }

    private static BlogAuthorResult authorOf(UUID personId, Map<UUID, PublicProfile> profiles) {
        PublicProfile profile = personId == null ? null : profiles.get(personId);
        return profile == null ? null : new BlogAuthorResult(profile.id(), profile.displayName(), profile.avatarUrl());
    }

    private static NotFoundException targetNotFound(BlogTargetType type, UUID targetId) {
        return new NotFoundException("BLOG_TARGET_NOT_FOUND",
                "No %s with id %s".formatted(type.name().toLowerCase(java.util.Locale.ROOT), targetId));
    }

    private static ConflictException noOpenReports() {
        return new ConflictException("BLOG_NO_OPEN_REPORTS", "There are no open reports to dismiss");
    }

    /** Never thrown while {@code act} routes DISMISS to {@link #dismiss}; there so the switches name every case. */
    private static IllegalStateException dismissIsNotAChange() {
        return new IllegalStateException("Dismissing reports does not change the content");
    }
}
