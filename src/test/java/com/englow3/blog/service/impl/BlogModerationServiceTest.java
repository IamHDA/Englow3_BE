package com.englow3.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.blog.dto.command.ModerationActionCommand;
import com.englow3.blog.dto.command.ModerationQueueFilter;
import com.englow3.blog.dto.projection.QueueRow;
import com.englow3.blog.dto.projection.TargetPreviewRow;
import com.englow3.blog.dto.result.ModerationActionResult;
import com.englow3.blog.dto.result.ModerationQueueItemResult;
import com.englow3.blog.dto.result.ModerationTargetResult;
import com.englow3.blog.entity.BlogComment;
import com.englow3.blog.entity.BlogCommentStatus;
import com.englow3.blog.entity.BlogModerationAction;
import com.englow3.blog.entity.BlogModerationActionType;
import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.entity.BlogPostStatus;
import com.englow3.blog.entity.BlogQueueSort;
import com.englow3.blog.entity.BlogReport;
import com.englow3.blog.entity.BlogReportReason;
import com.englow3.blog.entity.BlogReportStatus;
import com.englow3.blog.entity.BlogTargetType;
import com.englow3.blog.query.BlogModerationQueueQuery;
import com.englow3.blog.repository.BlogCommentRepository;
import com.englow3.blog.repository.BlogModerationActionRepository;
import com.englow3.blog.repository.BlogPostRepository;
import com.englow3.blog.repository.BlogReportRepository;
import com.englow3.blog.service.BlogModerationService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;
import com.englow3.user.api.UserDirectory;

/**
 * Orchestration only: the order things are asked and written in, and what is not touched when something refuses. The
 * rules of {@link BlogPost}, {@link BlogComment}, {@link BlogReport} and {@link BlogModerationAction} have their own
 * tests; where a refusal comes from a real entity here, the test is about what the service then does not do.
 */
class BlogModerationServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final PublicProfileReader publicProfileReader = mock(PublicProfileReader.class);
    private final BlogPostRepository postRepo = mock(BlogPostRepository.class);
    private final BlogCommentRepository commentRepo = mock(BlogCommentRepository.class);
    private final BlogReportRepository reportRepo = mock(BlogReportRepository.class);
    private final BlogModerationActionRepository actionRepo = mock(BlogModerationActionRepository.class);
    private final BlogModerationQueueQuery queueQuery = mock(BlogModerationQueueQuery.class);
    private final BlogImageLinks imageLinks = mock(BlogImageLinks.class);
    private final Instant now = Instant.parse("2026-11-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final BlogModerationService service = new BlogModerationServiceImpl(userDirectory, publicProfileReader,
            postRepo, commentRepo, reportRepo, actionRepo, queueQuery, imageLinks, clock);

    private final UUID me = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();
    private final UUID reporter = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        when(actionRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(publicProfileReader.profiles(any())).thenAnswer(invocation -> {
            Map<UUID, PublicProfile> profiles = new java.util.HashMap<>();
            for (UUID id : (Collection<UUID>) invocation.getArgument(0)) {
                profiles.put(id, new PublicProfile(id, "Name of " + id.toString().substring(0, 4), null));
            }
            return profiles;
        });
    }

    // --- helpers ---------------------------------------------------------------------------------------------------

    /** A real post, found whichever way the service asks for it. */
    private BlogPost postBy(UUID authorId) {
        BlogPost post = BlogPost.publish(authorId, "A title", "A body", Set.of(), now.minusSeconds(3600));
        when(postRepo.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(postRepo.findWithTagsById(post.getId())).thenReturn(Optional.of(post));
        when(postRepo.findById(post.getId())).thenReturn(Optional.of(post));
        return post;
    }

    private BlogComment commentBy(UUID authorId, UUID postId) {
        BlogComment comment = BlogComment.write(postId, authorId, "A comment", now.minusSeconds(60));
        when(commentRepo.findByIdForUpdate(comment.getId())).thenReturn(Optional.of(comment));
        when(commentRepo.findById(comment.getId())).thenReturn(Optional.of(comment));
        return comment;
    }

    private static void setStatus(BlogPost post, BlogPostStatus status) {
        ReflectionTestUtils.setField(post, "status", status);
    }

    private BlogReport openReport(BlogTargetType type, UUID target) {
        BlogReport report = BlogReport.open(reporter, type, target, BlogReportReason.SPAM, null, now.minusSeconds(600));
        when(reportRepo.findFirstByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(type, target,
                BlogReportStatus.OPEN)).thenReturn(Optional.of(report));
        return report;
    }

    private ModerationActionCommand command(BlogModerationActionType action) {
        return new ModerationActionCommand(action, "  Because I said so  ");
    }

    private BlogModerationAction savedAction() {
        ArgumentCaptor<BlogModerationAction> saved = ArgumentCaptor.forClass(BlogModerationAction.class);
        verify(actionRepo).save(saved.capture());
        return saved.getValue();
    }

    private void nothingIsLockedClosedOrRecorded() {
        verify(postRepo, never()).findByIdForUpdate(any());
        verify(commentRepo, never()).findByIdForUpdate(any());
        verify(reportRepo, never()).closeOpen(any(), any(), any(), any(), any(), any());
        verify(actionRepo, never()).save(any());
    }

    // --- act: hide, restore, remove --------------------------------------------------------------------------------

    @Nested
    class ChangingContent {

        @Test
        void hidingLocksThePostBeforeChangingItThenRecordsTheActionAndClosesTheReportsAsResolved() {
            BlogPost post = postBy(author);
            BlogReport lastOpen = openReport(BlogTargetType.POST, post.getId());

            service.act(BlogTargetType.POST, post.getId(), command(BlogModerationActionType.HIDE));

            assertThat(post.getStatus()).isEqualTo(BlogPostStatus.HIDDEN);
            assertThat(post.getUpdatedAt()).isEqualTo(now);
            InOrder order = inOrder(postRepo, reportRepo, actionRepo);
            order.verify(postRepo).existsByIdAndAuthorId(post.getId(), me);
            order.verify(postRepo).findByIdForUpdate(post.getId());
            order.verify(reportRepo).findFirstByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(BlogTargetType.POST,
                    post.getId(), BlogReportStatus.OPEN);
            order.verify(actionRepo).save(any(BlogModerationAction.class));
            order.verify(reportRepo).closeOpen(BlogTargetType.POST, post.getId(), BlogReportStatus.RESOLVED, me,
                    "Because I said so", now);
            BlogModerationAction action = savedAction();
            assertThat(action.getAction()).isEqualTo(BlogModerationActionType.HIDE);
            assertThat(action.getTargetType()).isEqualTo(BlogTargetType.POST);
            assertThat(action.getTargetId()).isEqualTo(post.getId());
            assertThat(action.getModeratorId()).isEqualTo(me);
            assertThat(action.getReason()).isEqualTo("Because I said so");
            assertThat(action.getReportId()).isEqualTo(lastOpen.getId());
            assertThat(action.getCreatedAt()).isEqualTo(now);
        }

        @Test
        void removingClosesTheReportsAsResolvedToo() {
            BlogPost post = postBy(author);

            service.act(BlogTargetType.POST, post.getId(), command(BlogModerationActionType.REMOVE));

            assertThat(post.getStatus()).isEqualTo(BlogPostStatus.REMOVED);
            verify(reportRepo).closeOpen(BlogTargetType.POST, post.getId(), BlogReportStatus.RESOLVED, me,
                    "Because I said so", now);
            assertThat(savedAction().getAction()).isEqualTo(BlogModerationActionType.REMOVE);
        }

        @Test
        void restoringRecordsTheActionButLeavesTheReportsAlone() {
            BlogPost post = postBy(author);
            setStatus(post, BlogPostStatus.HIDDEN);

            service.act(BlogTargetType.POST, post.getId(), command(BlogModerationActionType.RESTORE));

            assertThat(post.getStatus()).isEqualTo(BlogPostStatus.PUBLISHED);
            verify(reportRepo, never()).closeOpen(any(), any(), any(), any(), any(), any());
            assertThat(savedAction().getAction()).isEqualTo(BlogModerationActionType.RESTORE);
        }

        @Test
        void aModeratorMayActOnContentThatWasNeverReportedAndTheActionPointsAtNoReport() {
            BlogPost post = postBy(author);
            when(reportRepo.findFirstByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(any(), any(), any()))
                    .thenReturn(Optional.empty());

            service.act(BlogTargetType.POST, post.getId(), command(BlogModerationActionType.HIDE));

            assertThat(savedAction().getReportId()).isNull();
            assertThat(post.getStatus()).isEqualTo(BlogPostStatus.HIDDEN);
        }

        @Test
        void aCommentIsLockedAndChangedTheSameWay() {
            BlogComment comment = commentBy(author, UUID.randomUUID());
            BlogReport lastOpen = openReport(BlogTargetType.COMMENT, comment.getId());

            service.act(BlogTargetType.COMMENT, comment.getId(), command(BlogModerationActionType.HIDE));

            assertThat(comment.getStatus()).isEqualTo(BlogCommentStatus.HIDDEN);
            InOrder order = inOrder(commentRepo, reportRepo);
            order.verify(commentRepo).existsByIdAndAuthorId(comment.getId(), me);
            order.verify(commentRepo).findByIdForUpdate(comment.getId());
            order.verify(reportRepo).closeOpen(BlogTargetType.COMMENT, comment.getId(), BlogReportStatus.RESOLVED, me,
                    "Because I said so", now);
            assertThat(savedAction().getReportId()).isEqualTo(lastOpen.getId());
            verify(postRepo, never()).findByIdForUpdate(any());
        }

        @Test
        void contentThatDoesNotExistIsNotFoundAndNothingIsRecordedOrClosed() {
            UUID missing = UUID.randomUUID();
            when(postRepo.findByIdForUpdate(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.act(BlogTargetType.POST, missing, command(BlogModerationActionType.HIDE)))
                    .isInstanceOfSatisfying(NotFoundException.class,
                            e -> assertThat(e.getCode()).isEqualTo("BLOG_TARGET_NOT_FOUND"));

            verify(reportRepo, never()).closeOpen(any(), any(), any(), any(), any(), any());
            verify(actionRepo, never()).save(any());
        }

        @Test
        void aChangeTheEntityRefusesIsNeverRecordedAndClosesNoReport() {
            BlogPost post = postBy(author);
            setStatus(post, BlogPostStatus.REMOVED);
            openReport(BlogTargetType.POST, post.getId());

            assertThatThrownBy(
                    () -> service.act(BlogTargetType.POST, post.getId(), command(BlogModerationActionType.RESTORE)))
                            .isInstanceOf(ConflictException.class);

            verify(reportRepo, never()).closeOpen(any(), any(), any(), any(), any(), any());
            verify(actionRepo, never()).save(any());
            assertThat(post.getStatus()).isEqualTo(BlogPostStatus.REMOVED);
        }

        @Test
        void theAnswerIsTheContentReadAgainAfterTheWrite() {
            BlogPost post = postBy(author);

            ModerationTargetResult result = service.act(BlogTargetType.POST, post.getId(),
                    command(BlogModerationActionType.HIDE));

            InOrder order = inOrder(reportRepo, postRepo);
            order.verify(reportRepo).closeOpen(any(), any(), any(), any(), any(), any());
            order.verify(postRepo).findWithTagsById(post.getId());
            assertThat(result.targetStatus()).isEqualTo("HIDDEN");
            assertThat(result.content().title()).isEqualTo("A title");
        }
    }

    // --- act: dismiss ----------------------------------------------------------------------------------------------

    @Nested
    class Dismissing {

        @Test
        void theOpenReportsAreClosedAsDismissedOneActionIsRecordedAndTheContentIsNeitherLockedNorChanged() {
            BlogPost post = postBy(author);
            BlogReport lastOpen = openReport(BlogTargetType.POST, post.getId());
            when(reportRepo.closeOpen(any(), any(), any(), any(), any(), any())).thenReturn(3);

            service.act(BlogTargetType.POST, post.getId(), command(BlogModerationActionType.DISMISS));

            verify(reportRepo).closeOpen(BlogTargetType.POST, post.getId(), BlogReportStatus.DISMISSED, me,
                    "Because I said so", now);
            BlogModerationAction action = savedAction();
            assertThat(action.getAction()).isEqualTo(BlogModerationActionType.DISMISS);
            assertThat(action.getReportId()).isEqualTo(lastOpen.getId());
            assertThat(action.getReason()).isEqualTo("Because I said so");
            verify(postRepo, never()).findByIdForUpdate(any());
            assertThat(post.getStatus()).isEqualTo(BlogPostStatus.PUBLISHED);
        }

        @Test
        void nothingToCloseIsRefusedAndNothingIsRecordedAndNothingIsLocked() {
            BlogPost post = postBy(author);
            openReport(BlogTargetType.POST, post.getId());
            // Another moderator closed them between the read and the statement.
            when(reportRepo.closeOpen(any(), any(), any(), any(), any(), any())).thenReturn(0);

            assertThatThrownBy(
                    () -> service.act(BlogTargetType.POST, post.getId(), command(BlogModerationActionType.DISMISS)))
                            .isInstanceOfSatisfying(ConflictException.class,
                                    e -> assertThat(e.getCode()).isEqualTo("BLOG_NO_OPEN_REPORTS"));

            verify(actionRepo, never()).save(any());
            verify(postRepo, never()).findByIdForUpdate(any());
        }

        @Test
        void noOpenReportToStartWithIsRefusedBeforeAnythingIsClosed() {
            BlogPost post = postBy(author);
            when(reportRepo.findFirstByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                    () -> service.act(BlogTargetType.POST, post.getId(), command(BlogModerationActionType.DISMISS)))
                            .isInstanceOfSatisfying(ConflictException.class,
                                    e -> assertThat(e.getCode()).isEqualTo("BLOG_NO_OPEN_REPORTS"));

            verify(reportRepo, never()).closeOpen(any(), any(), any(), any(), any(), any());
            verify(actionRepo, never()).save(any());
        }

        /** The author deleted the post after it was reported; the reports are all that is left, and can be cleared. */
        @Test
        void contentItsAuthorHasDeletedCanStillHaveItsReportsDismissed() {
            UUID gone = UUID.randomUUID();
            openReport(BlogTargetType.POST, gone);
            when(reportRepo.closeOpen(any(), any(), any(), any(), any(), any())).thenReturn(2);
            when(postRepo.findWithTagsById(gone)).thenReturn(Optional.empty());
            when(actionRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(BlogTargetType.POST, gone))
                    .thenReturn(List.of(BlogModerationAction.record(BlogTargetType.POST, gone,
                            BlogModerationActionType.DISMISS, "Gone", me, null, now)));

            ModerationTargetResult result = service.act(BlogTargetType.POST, gone,
                    command(BlogModerationActionType.DISMISS));

            assertThat(result.targetStatus()).isEqualTo("DELETED");
            assertThat(result.content()).isNull();
            assertThat(savedAction().getAction()).isEqualTo(BlogModerationActionType.DISMISS);
        }
    }

    // --- act: what is refused before anything is touched -----------------------------------------------------------

    @Nested
    class RefusingBeforeAnythingIsTouched {

        @Test
        void aModeratorsOwnPostIsRefusedForEveryActionBeforeAnythingIsLockedOrWritten() {
            BlogPost post = postBy(me);
            when(postRepo.existsByIdAndAuthorId(post.getId(), me)).thenReturn(true);

            for (BlogModerationActionType action : BlogModerationActionType.values()) {
                assertThatThrownBy(() -> service.act(BlogTargetType.POST, post.getId(), command(action)))
                        .isInstanceOfSatisfying(ForbiddenException.class,
                                e -> assertThat(e.getCode()).isEqualTo("BLOG_MODERATION_OWN_CONTENT"));
            }

            nothingIsLockedClosedOrRecorded();
            verify(reportRepo, never()).findFirstByTargetTypeAndTargetIdAndStatusOrderByCreatedAtDesc(any(), any(),
                    any());
            assertThat(post.getStatus()).isEqualTo(BlogPostStatus.PUBLISHED);
        }

        @Test
        void aModeratorsOwnCommentIsRefusedForEveryActionBeforeAnythingIsLockedOrWritten() {
            BlogComment comment = commentBy(me, UUID.randomUUID());
            when(commentRepo.existsByIdAndAuthorId(comment.getId(), me)).thenReturn(true);

            for (BlogModerationActionType action : BlogModerationActionType.values()) {
                assertThatThrownBy(() -> service.act(BlogTargetType.COMMENT, comment.getId(), command(action)))
                        .isInstanceOf(ForbiddenException.class);
            }

            nothingIsLockedClosedOrRecorded();
            assertThat(comment.getStatus()).isEqualTo(BlogCommentStatus.VISIBLE);
        }

        @Test
        void aReasonThatIsBlankOrTooLongIsRefusedBeforeAnythingIsAskedOfTheDatabase() {
            UUID target = UUID.randomUUID();

            for (String reason : new String[] { null, "", "  \n ", "x".repeat(501) }) {
                assertThatThrownBy(() -> service.act(BlogTargetType.POST, target,
                        new ModerationActionCommand(BlogModerationActionType.HIDE, reason))).isInstanceOfSatisfying(
                                BadRequestException.class,
                                e -> assertThat(e.getCode()).isEqualTo("BLOG_MODERATION_REASON_INVALID"));
            }

            verifyNoInteractions(postRepo, commentRepo, reportRepo, actionRepo);
        }
    }

    // --- the queue -------------------------------------------------------------------------------------------------

    @Nested
    class TheQueue {

        private final UUID postId = UUID.randomUUID();
        private final UUID commentId = UUID.randomUUID();
        private final UUID commentAuthor = UUID.randomUUID();
        private final UUID deletedId = UUID.randomUUID();

        private QueueRow row(BlogTargetType type, UUID id, long open) {
            return new QueueRow(type, id, open, List.of(BlogReportReason.SPAM), now.minusSeconds(100), now);
        }

        @Test
        void authorsAreLookedUpOnceForThePageAndEveryRowCarriesWhatTheQueueNeeds() {
            when(queueQuery.queue(any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(row(BlogTargetType.POST, postId, 3), row(BlogTargetType.COMMENT, commentId, 1),
                            row(BlogTargetType.POST, deletedId, 2)));
            when(queueQuery.previews(any(), any())).thenReturn(List.of(
                    new TargetPreviewRow(BlogTargetType.POST, postId, author, "A post",
                            "# A post\n\nSome **bold** text ![d](blog-image:6f1c2a40-1b2c-4d3e-8f90-a1b2c3d4e5f6) end",
                            "PUBLISHED", now, null),
                    new TargetPreviewRow(BlogTargetType.COMMENT, commentId, commentAuthor, "The post it is on",
                            "a *plain* comment", "VISIBLE", now, UUID.randomUUID())));

            List<ModerationQueueItemResult> items = service
                    .queue(new ModerationQueueFilter(null, null), PageRequest.of(0, 20)).getContent();

            verify(publicProfileReader, times(1)).profiles(Set.of(author, commentAuthor));
            assertThat(items).hasSize(3);
            ModerationQueueItemResult post = items.get(0);
            assertThat(post.title()).isEqualTo("A post");
            assertThat(post.excerpt()).isEqualTo("A post Some bold text end");
            assertThat(post.author().userId()).isEqualTo(author);
            assertThat(post.targetStatus()).isEqualTo("PUBLISHED");
            assertThat(post.openReports()).isEqualTo(3);
            assertThat(post.reasons()).containsExactly(BlogReportReason.SPAM);
            ModerationQueueItemResult comment = items.get(1);
            assertThat(comment.title()).isEqualTo("The post it is on");
            // A comment is plain text: nothing is stripped from it.
            assertThat(comment.excerpt()).isEqualTo("a *plain* comment");
            assertThat(comment.author().userId()).isEqualTo(commentAuthor);
            ModerationQueueItemResult deleted = items.get(2);
            assertThat(deleted.targetStatus()).isEqualTo("DELETED");
            assertThat(deleted.title()).isNull();
            assertThat(deleted.excerpt()).isNull();
            assertThat(deleted.author()).isNull();
            assertThat(deleted.openReports()).isEqualTo(2);
        }

        @Test
        void thePreviewsAreAskedOnceForTheWholePageSplitIntoPostsAndComments() {
            when(queueQuery.queue(any(), any(), anyInt(), anyInt())).thenReturn(
                    List.of(row(BlogTargetType.POST, postId, 1), row(BlogTargetType.COMMENT, commentId, 1)));

            service.queue(new ModerationQueueFilter(null, null), PageRequest.of(0, 20));

            verify(queueQuery, times(1)).previews(Set.of(postId), Set.of(commentId));
        }

        @Test
        void anEmptyPageAsksNobodyForProfiles() {
            when(queueQuery.queue(any(), any(), anyInt(), anyInt())).thenReturn(List.of());

            var page = service.queue(new ModerationQueueFilter(null, null), PageRequest.of(0, 20));

            assertThat(page.getContent()).isEmpty();
            verifyNoInteractions(publicProfileReader);
        }

        @Test
        void aPageOfOnlyDeletedContentAsksNobodyForProfiles() {
            when(queueQuery.queue(any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(row(BlogTargetType.POST, deletedId, 1)));
            when(queueQuery.previews(any(), any())).thenReturn(List.of());

            service.queue(new ModerationQueueFilter(null, null), PageRequest.of(0, 20));

            verifyNoInteractions(publicProfileReader);
        }

        @Test
        void theFilterTheSortAndThePagingGoToTheQuery() {
            when(queueQuery.queue(any(), any(), anyInt(), anyInt())).thenReturn(List.of());

            service.queue(new ModerationQueueFilter(BlogTargetType.COMMENT, BlogQueueSort.MOST_REPORTED),
                    PageRequest.of(2, 10));

            verify(queueQuery).queue(BlogTargetType.COMMENT, BlogQueueSort.MOST_REPORTED, 10, 20);
        }

        @Test
        void noSortIsOldestFirstAndAPageIsAtMostFifty() {
            when(queueQuery.queue(any(), any(), anyInt(), anyInt())).thenReturn(List.of());

            var page = service.queue(new ModerationQueueFilter(null, null), PageRequest.of(1, 500));

            verify(queueQuery).queue(null, BlogQueueSort.OLDEST, 50, 50);
            assertThat(page.getSize()).isEqualTo(50);
        }

        @Test
        void theTotalIsTheNumberOfPiecesOfContentOfTheSameKind() {
            when(queueQuery.queue(any(), any(), anyInt(), anyInt()))
                    .thenReturn(List.of(row(BlogTargetType.POST, postId, 1)));
            when(queueQuery.previews(any(), any())).thenReturn(List.of());
            when(queueQuery.count(BlogTargetType.POST)).thenReturn(41L);

            var page = service.queue(new ModerationQueueFilter(BlogTargetType.POST, null), PageRequest.of(0, 1));

            assertThat(page.getTotalElements()).isEqualTo(41);
        }

        @Test
        void aCommentExcerptIsCutAt200CharactersWithAnEllipsisAndNotInsideACharacter() {
            assertThat(BlogModerationServiceImpl.commentExcerpt("short")).isEqualTo("short");
            assertThat(BlogModerationServiceImpl.commentExcerpt("x".repeat(200))).hasSize(200).doesNotContain("…");
            assertThat(BlogModerationServiceImpl.commentExcerpt("x".repeat(400))).hasSize(200).endsWith("…");
            // 198 units, then an emoji of two: the cut at 199 would fall between its halves.
            assertThat(BlogModerationServiceImpl.commentExcerpt("x".repeat(198) + "😀😀"))
                    .isEqualTo("x".repeat(198) + "…");
        }
    }

    // --- one piece of content --------------------------------------------------------------------------------------

    @Nested
    class TheTargetView {

        @Test
        void aPostIsShownWithItsReportsAndHistoryAndEveryPersonIsLookedUpOnce() {
            BlogPost post = postBy(author);
            BlogReport report = BlogReport.open(reporter, BlogTargetType.POST, post.getId(), BlogReportReason.SPAM,
                    "an advert", now.minusSeconds(60));
            when(reportRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(BlogTargetType.POST, post.getId()))
                    .thenReturn(List.of(report));
            when(actionRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(BlogTargetType.POST, post.getId()))
                    .thenReturn(List.of(BlogModerationAction.record(BlogTargetType.POST, post.getId(),
                            BlogModerationActionType.DISMISS, "Fine", me, report.getId(), now)));

            ModerationTargetResult result = service.target(BlogTargetType.POST, post.getId());

            verify(publicProfileReader, times(1)).profiles(Set.of(author, reporter, me));
            assertThat(result.targetStatus()).isEqualTo("PUBLISHED");
            assertThat(result.author().userId()).isEqualTo(author);
            assertThat(result.content().title()).isEqualTo("A title");
            assertThat(result.content().body()).isEqualTo("A body");
            assertThat(result.content().postId()).isNull();
            assertThat(result.reports()).singleElement().satisfies(shown -> {
                assertThat(shown.reporter().userId()).isEqualTo(reporter);
                assertThat(shown.detail()).isEqualTo("an advert");
                assertThat(shown.status()).isEqualTo(BlogReportStatus.OPEN);
            });
            assertThat(result.actions()).singleElement().satisfies(shown -> {
                assertThat(shown.moderator().userId()).isEqualTo(me);
                assertThat(shown.action()).isEqualTo(BlogModerationActionType.DISMISS);
            });
        }

        @Test
        void aHiddenPostIsShownToo() {
            BlogPost post = postBy(author);
            setStatus(post, BlogPostStatus.REMOVED);

            assertThat(service.target(BlogTargetType.POST, post.getId()).targetStatus()).isEqualTo("REMOVED");
        }

        @Test
        void aCommentIsShownWithThePostItIsOn() {
            BlogPost post = postBy(author);
            BlogComment comment = commentBy(reporter, post.getId());

            ModerationTargetResult result = service.target(BlogTargetType.COMMENT, comment.getId());

            assertThat(result.targetStatus()).isEqualTo("VISIBLE");
            assertThat(result.content().title()).isEqualTo("A title");
            assertThat(result.content().postId()).isEqualTo(post.getId());
            assertThat(result.content().body()).isEqualTo("A comment");
            assertThat(result.content().images()).isEmpty();
            assertThat(result.content().tags()).isEmpty();
            assertThat(result.author().userId()).isEqualTo(reporter);
        }

        @Test
        void contentThatIsGoneIsDescribedByWhatRemainsOfIt() {
            UUID gone = UUID.randomUUID();
            when(postRepo.findWithTagsById(gone)).thenReturn(Optional.empty());
            when(reportRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(BlogTargetType.POST, gone)).thenReturn(
                    List.of(BlogReport.open(reporter, BlogTargetType.POST, gone, BlogReportReason.OTHER, null, now)));

            ModerationTargetResult result = service.target(BlogTargetType.POST, gone);

            assertThat(result.targetStatus()).isEqualTo("DELETED");
            assertThat(result.content()).isNull();
            assertThat(result.author()).isNull();
            assertThat(result.reports()).hasSize(1);
        }

        @Test
        void nothingAtAllIsNotFoundAndNobodyIsLookedUp() {
            UUID nothing = UUID.randomUUID();
            when(postRepo.findWithTagsById(nothing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.target(BlogTargetType.POST, nothing)).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_TARGET_NOT_FOUND"));

            verifyNoInteractions(publicProfileReader);
        }

        @Test
        void aPostWhoseAuthorIsErasedHasNoAuthorAndNobodyIsAskedForOne() {
            BlogPost orphan = postBy(null);

            ModerationTargetResult result = service.target(BlogTargetType.POST, orphan.getId());

            assertThat(result.author()).isNull();
            verifyNoInteractions(publicProfileReader);
        }
    }

    // --- history ---------------------------------------------------------------------------------------------------

    @Nested
    class TheHistory {

        @Test
        void moderatorsAreLookedUpOnceForThePageAndAPageIsAtMostFifty() {
            UUID otherModerator = UUID.randomUUID();
            BlogModerationAction first = BlogModerationAction.record(BlogTargetType.POST, UUID.randomUUID(),
                    BlogModerationActionType.HIDE, "Spam", me, null, now);
            BlogModerationAction second = BlogModerationAction.record(BlogTargetType.COMMENT, UUID.randomUUID(),
                    BlogModerationActionType.DISMISS, "Fine", otherModerator, null, now);
            ArgumentCaptor<org.springframework.data.domain.Pageable> paging = ArgumentCaptor
                    .forClass(org.springframework.data.domain.Pageable.class);
            when(actionRepo.findAllByOrderByCreatedAtDesc(paging.capture()))
                    .thenReturn(new PageImpl<>(List.of(first, second)));

            List<ModerationActionResult> actions = service.recentActions(PageRequest.of(3, 500)).getContent();

            assertThat(paging.getValue().getPageSize()).isEqualTo(50);
            assertThat(paging.getValue().getPageNumber()).isEqualTo(3);
            verify(publicProfileReader, times(1)).profiles(Set.of(me, otherModerator));
            assertThat(actions).extracting(ModerationActionResult::action)
                    .containsExactly(BlogModerationActionType.HIDE, BlogModerationActionType.DISMISS);
            assertThat(actions.get(0).moderator().userId()).isEqualTo(me);
            assertThat(actions.get(1).reason()).isEqualTo("Fine");
        }

        @Test
        void aPageFurtherOnThanTheDatabaseCanSkipIsEmptyWithTheRealTotalAndIsNeverQueried() {
            when(actionRepo.count()).thenReturn(7L);

            var page = service.recentActions(PageRequest.of(Integer.MAX_VALUE / 2, 50));

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isEqualTo(7);
            verify(actionRepo, never()).findAllByOrderByCreatedAtDesc(any());
        }

        @Test
        void anEmptyHistoryAsksNobodyForProfiles() {
            when(actionRepo.findAllByOrderByCreatedAtDesc(any())).thenReturn(new PageImpl<>(List.of()));

            assertThat(service.recentActions(PageRequest.of(0, 20)).getContent()).isEmpty();

            verifyNoInteractions(publicProfileReader);
        }
    }
}
