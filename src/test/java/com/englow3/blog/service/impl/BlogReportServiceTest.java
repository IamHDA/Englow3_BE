package com.englow3.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.blog.dto.command.ReportContentCommand;
import com.englow3.blog.dto.result.ReportOutcome;
import com.englow3.blog.entity.BlogComment;
import com.englow3.blog.entity.BlogCommentStatus;
import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.entity.BlogPostStatus;
import com.englow3.blog.entity.BlogReport;
import com.englow3.blog.entity.BlogReportReason;
import com.englow3.blog.entity.BlogReportStatus;
import com.englow3.blog.entity.BlogTargetType;
import com.englow3.blog.repository.BlogCommentRepository;
import com.englow3.blog.repository.BlogPostRepository;
import com.englow3.blog.repository.BlogReportRepository;
import com.englow3.blog.service.BlogReportService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.error.TooManyRequestsException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.user.api.UserDirectory;

/**
 * Orchestration only: what is asked in which order, and what is never written when something refuses. The read rule has
 * its own test and is mocked here; the rules of {@link BlogReport} and {@link BlogPost} have theirs, so where a refusal
 * comes from the real entity the test is about what the service then does not do.
 */
class BlogReportServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final BlogReadRule readRule = mock(BlogReadRule.class);
    private final BlogPostRepository postRepo = mock(BlogPostRepository.class);
    private final BlogCommentRepository commentRepo = mock(BlogCommentRepository.class);
    private final BlogReportRepository reportRepo = mock(BlogReportRepository.class);
    private final UserRateLimiter rateLimiter = mock(UserRateLimiter.class);
    private final Instant now = Instant.parse("2026-11-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final BlogReportService service = new BlogReportServiceImpl(userDirectory, readRule, postRepo, commentRepo,
            reportRepo, rateLimiter, clock);

    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final ReportContentCommand command = new ReportContentCommand(BlogReportReason.SPAM, "  an advert  ");

    @BeforeEach
    void setUp() {
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        // Open to everyone unless a test says the rule refuses, and nobody is blocked with anybody.
        when(readRule.canRead(any(), eq(me))).thenReturn(true);
    }

    private BlogPost postBy(UUID author) {
        BlogPost post = BlogPost.publish(author, "Title", "body", Set.of(), now.minusSeconds(3600));
        when(postRepo.findById(post.getId())).thenReturn(Optional.of(post));
        return post;
    }

    private static void hide(BlogPost post) {
        ReflectionTestUtils.setField(post, "status", BlogPostStatus.HIDDEN);
    }

    private BlogComment commentBy(UUID author, UUID postId) {
        BlogComment comment = BlogComment.write(postId, author, "A comment", now.minusSeconds(60));
        when(commentRepo.findById(comment.getId())).thenReturn(Optional.of(comment));
        return comment;
    }

    /** The reader's own open report, as the repository would give it back. */
    private BlogReport openReportOf(BlogTargetType type, UUID target, String detail) {
        BlogReport existing = BlogReport.open(me, type, target, BlogReportReason.HARASSMENT, detail,
                now.minusSeconds(600));
        when(reportRepo.findByReporterIdAndTargetTypeAndTargetIdAndStatus(me, type, target, BlogReportStatus.OPEN))
                .thenReturn(Optional.of(existing));
        return existing;
    }

    private void nothingIsFiled() {
        verify(reportRepo, never()).insertIfAbsent(any(), any(), anyString(), any(), anyString(), any(), any());
    }

    @Nested
    class ReportingAPost {

        @Test
        void everyReportCountsAgainstTheReportRateLimit() {
            BlogPost post = postBy(other);
            openReportOf(BlogTargetType.POST, post.getId(), null);

            service.reportPost(post.getId(), command);

            verify(rateLimiter).check(me, "blog-report", 10, Duration.ofHours(1));
        }

        @Test
        void aReaderOverTheLimitIsRefusedBeforeAnythingIsRead() {
            doThrow(new TooManyRequestsException("RATE_LIMITED", "slow down")).when(rateLimiter).check(eq(me),
                    eq("blog-report"), anyInt(), any());

            assertThatThrownBy(() -> service.reportPost(UUID.randomUUID(), command))
                    .isInstanceOf(TooManyRequestsException.class);

            verifyNoInteractions(postRepo, reportRepo, readRule);
        }

        @Test
        void aNewReportIsInsertedWithTheValuesTheEntityCheckedAndIsReturnedAsCreated() {
            BlogPost post = postBy(other);
            when(reportRepo.insertIfAbsent(any(), any(), anyString(), any(), anyString(), any(), any())).thenReturn(1);
            BlogReport stored = openReportOf(BlogTargetType.POST, post.getId(), "an advert");

            ReportOutcome outcome = service.reportPost(post.getId(), command);

            ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
            verify(reportRepo).insertIfAbsent(id.capture(), eq(me), eq("POST"), eq(post.getId()), eq("SPAM"),
                    eq("an advert"), eq(now));
            assertThat(id.getValue()).isNotNull();
            assertThat(outcome.created()).isTrue();
            assertThat(outcome.report().id()).isEqualTo(stored.getId());
            assertThat(outcome.report().targetType()).isEqualTo(BlogTargetType.POST);
            assertThat(outcome.report().status()).isEqualTo(BlogReportStatus.OPEN);
        }

        @Test
        void aReportNoteThatIsBlankIsInsertedAsNoNote() {
            BlogPost post = postBy(other);
            openReportOf(BlogTargetType.POST, post.getId(), null);

            service.reportPost(post.getId(), new ReportContentCommand(BlogReportReason.OTHER, "   "));

            verify(reportRepo).insertIfAbsent(any(), eq(me), eq("POST"), eq(post.getId()), eq("OTHER"), eq(null),
                    eq(now));
        }

        @Test
        void whenTheReaderAlreadyHasAnOpenReportThatOneIsReturnedAndNothingNewIsCreated() {
            BlogPost post = postBy(other);
            when(reportRepo.insertIfAbsent(any(), any(), anyString(), any(), anyString(), any(), any())).thenReturn(0);
            BlogReport existing = openReportOf(BlogTargetType.POST, post.getId(), "the first note");

            ReportOutcome outcome = service.reportPost(post.getId(), command);

            assertThat(outcome.created()).isFalse();
            assertThat(outcome.report().id()).isEqualTo(existing.getId());
            assertThat(outcome.report().detail()).isEqualTo("the first note");
            assertThat(outcome.report().reason()).isEqualTo(BlogReportReason.HARASSMENT);
        }

        @Test
        void theInsertIsMadeBeforeTheOpenReportIsReadBack() {
            BlogPost post = postBy(other);
            openReportOf(BlogTargetType.POST, post.getId(), null);

            service.reportPost(post.getId(), command);

            InOrder order = inOrder(reportRepo);
            order.verify(reportRepo).insertIfAbsent(any(), any(), anyString(), any(), anyString(), any(), any());
            order.verify(reportRepo).findByReporterIdAndTargetTypeAndTargetIdAndStatus(me, BlogTargetType.POST,
                    post.getId(), BlogReportStatus.OPEN);
        }

        @Test
        void aReportClosedBetweenTheInsertAndTheReadIsAskedAgainNotAnError500() {
            BlogPost post = postBy(other);
            when(reportRepo.findByReporterIdAndTargetTypeAndTargetIdAndStatus(any(), any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reportPost(post.getId(), command)).isInstanceOfSatisfying(
                    ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_REPORT_CLOSED"));
        }

        @Test
        void aPostTheReadRuleRefusesIsNotFoundAndNothingIsFiled() {
            BlogPost post = postBy(other);
            when(readRule.canRead(post, me)).thenReturn(false);

            assertThatThrownBy(() -> service.reportPost(post.getId(), command)).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

            nothingIsFiled();
        }

        @Test
        void aPostThatDoesNotExistIsNotFound() {
            UUID missing = UUID.randomUUID();
            when(postRepo.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reportPost(missing, command)).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

            nothingIsFiled();
        }

        @Test
        void theCallersOwnPostIsRefusedAndNothingIsFiled() {
            BlogPost post = postBy(me);

            assertThatThrownBy(() -> service.reportPost(post.getId(), command)).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_REPORT_OWN_CONTENT"));

            nothingIsFiled();
        }

        /** Not reachable through the read rule today (only an author reads such a post), but the report asks anyway. */
        @Test
        void aPostThatIsNotPublishedIsNotOpenToReportsAndNothingIsFiled() {
            BlogPost post = postBy(other);
            hide(post);

            assertThatThrownBy(() -> service.reportPost(post.getId(), command)).isInstanceOfSatisfying(
                    ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_OPEN"));

            nothingIsFiled();
        }

        @Test
        void aNoteTheEntityRefusesIsNeverInserted() {
            BlogPost post = postBy(other);

            assertThatThrownBy(() -> service.reportPost(post.getId(),
                    new ReportContentCommand(BlogReportReason.OTHER, "x".repeat(501))))
                            .isInstanceOf(BadRequestException.class);

            nothingIsFiled();
        }
    }

    @Nested
    class ReportingAComment {

        @Test
        void aNewReportIsInsertedForTheCommentAndCountsAgainstTheSameRateLimit() {
            BlogPost post = postBy(other);
            BlogComment comment = commentBy(other, post.getId());
            when(reportRepo.insertIfAbsent(any(), any(), anyString(), any(), anyString(), any(), any())).thenReturn(1);
            openReportOf(BlogTargetType.COMMENT, comment.getId(), "an advert");

            ReportOutcome outcome = service.reportComment(comment.getId(), command);

            verify(rateLimiter).check(me, "blog-report", 10, Duration.ofHours(1));
            verify(reportRepo).insertIfAbsent(any(), eq(me), eq("COMMENT"), eq(comment.getId()), eq("SPAM"),
                    eq("an advert"), eq(now));
            assertThat(outcome.created()).isTrue();
            assertThat(outcome.report().targetType()).isEqualTo(BlogTargetType.COMMENT);
        }

        @Test
        void whenTheReaderAlreadyHasAnOpenReportThatOneIsReturned() {
            BlogPost post = postBy(other);
            BlogComment comment = commentBy(other, post.getId());
            when(reportRepo.insertIfAbsent(any(), any(), anyString(), any(), anyString(), any(), any())).thenReturn(0);
            BlogReport existing = openReportOf(BlogTargetType.COMMENT, comment.getId(), null);

            ReportOutcome outcome = service.reportComment(comment.getId(), command);

            assertThat(outcome.created()).isFalse();
            assertThat(outcome.report().id()).isEqualTo(existing.getId());
        }

        @Test
        void aCommentThatDoesNotExistIsNotFound() {
            UUID missing = UUID.randomUUID();
            when(commentRepo.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reportComment(missing, command)).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_FOUND"));

            nothingIsFiled();
        }

        @Test
        void aCommentThatIsNotVisibleIsNotFound() {
            BlogPost post = postBy(other);
            BlogComment comment = commentBy(other, post.getId());
            ReflectionTestUtils.setField(comment, "status", BlogCommentStatus.HIDDEN);

            assertThatThrownBy(() -> service.reportComment(comment.getId(), command)).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_FOUND"));

            nothingIsFiled();
        }

        @Test
        void aCommentOnAPostTheReadRuleRefusesIsTheCommentsNotFoundNotThePosts() {
            BlogPost post = postBy(other);
            BlogComment comment = commentBy(other, post.getId());
            when(readRule.canRead(post, me)).thenReturn(false);

            assertThatThrownBy(() -> service.reportComment(comment.getId(), command)).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_FOUND"));

            nothingIsFiled();
        }

        @Test
        void aCommentWhosePostIsGoneIsNotFound() {
            BlogComment comment = commentBy(other, UUID.randomUUID());
            when(postRepo.findById(comment.getPostId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reportComment(comment.getId(), command))
                    .isInstanceOf(NotFoundException.class);

            nothingIsFiled();
        }

        @Test
        void aCommentByAnAuthorTheReaderHasABlockWithIsNotFound() {
            BlogPost post = postBy(UUID.randomUUID());
            BlogComment comment = commentBy(other, post.getId());
            when(readRule.isBlockedWith(other, me)).thenReturn(true);

            assertThatThrownBy(() -> service.reportComment(comment.getId(), command)).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_FOUND"));

            nothingIsFiled();
        }

        @Test
        void theCallersOwnCommentIsRefusedAndNothingIsFiled() {
            BlogPost post = postBy(other);
            BlogComment comment = commentBy(me, post.getId());

            assertThatThrownBy(() -> service.reportComment(comment.getId(), command)).isInstanceOfSatisfying(
                    BadRequestException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_REPORT_OWN_CONTENT"));

            nothingIsFiled();
        }
    }
}
