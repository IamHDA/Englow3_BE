package com.englow3.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.blog.dto.command.SaveCommentCommand;
import com.englow3.blog.dto.result.BlogCommentResult;
import com.englow3.blog.entity.BlogComment;
import com.englow3.blog.entity.BlogCommentStatus;
import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.entity.BlogPostStatus;
import com.englow3.blog.repository.BlogCommentRepository;
import com.englow3.blog.repository.BlogPostRepository;
import com.englow3.blog.service.BlogCommentService;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;
import com.englow3.user.api.UserDirectory;

/**
 * Orchestration only. The rules inside {@link BlogPost} and {@link BlogComment} have their own tests, and so has the
 * read rule ({@link BlogReadRule}, mocked here): where a post is hidden here it is the real entity, put in that state
 * by hand, and the test is about what the service then does not do.
 */
class BlogCommentServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final FriendshipReader friendshipReader = mock(FriendshipReader.class);
    private final PublicProfileReader publicProfileReader = mock(PublicProfileReader.class);
    private final BlogReadRule readRule = mock(BlogReadRule.class);
    private final BlogPostRepository postRepo = mock(BlogPostRepository.class);
    private final BlogCommentRepository commentRepo = mock(BlogCommentRepository.class);
    private final UserRateLimiter rateLimiter = mock(UserRateLimiter.class);
    private final Instant now = Instant.parse("2026-11-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final BlogCommentService service = new BlogCommentServiceImpl(userDirectory, friendshipReader,
            publicProfileReader, readRule, postRepo, commentRepo, rateLimiter, clock);

    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        when(friendshipReader.blockedEitherWay(any())).thenReturn(Set.of());
        // Whether a post may be read is BlogReadRule's to answer and has its own test; here it is open unless a test
        // says the rule refuses, and no one is blocked from commenting unless a test says so.
        when(readRule.canRead(any(), eq(me))).thenReturn(true);
        when(commentRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(publicProfileReader.profiles(any())).thenAnswer(invocation -> {
            Map<UUID, PublicProfile> profiles = new java.util.HashMap<>();
            for (UUID id : (Collection<UUID>) invocation.getArgument(0)) {
                profiles.put(id, new PublicProfile(id, "Name of " + id.toString().substring(0, 4), null));
            }
            return profiles;
        });
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
        // Only the locking finder: a service that read the comment any other way would find nothing here.
        when(commentRepo.findByIdForUpdate(comment.getId())).thenReturn(Optional.of(comment));
        return comment;
    }

    @Nested
    class Listing {

        private final Pageable firstPage = PageRequest.of(0, 20);

        @Test
        void aPostTheReadRuleRefusesHasNoCommentsToListAndNothingIsQueried() {
            BlogPost post = postBy(other);
            when(readRule.canRead(post, me)).thenReturn(false);

            assertThatThrownBy(() -> service.list(post.getId(), firstPage)).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

            verifyNoInteractions(commentRepo);
        }

        @Test
        void theQueryIsGivenTheCallersBlocksAndTheCallerSoTheirOwnHiddenCommentsAreIncluded() {
            BlogPost post = postBy(other);
            UUID blocked = UUID.randomUUID();
            when(friendshipReader.blockedEitherWay(me)).thenReturn(Set.of(blocked));
            when(commentRepo.findVisible(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

            service.list(post.getId(), firstPage);

            ArgumentCaptor<Collection<UUID>> hidden = ArgumentCaptor.forClass(Collection.class);
            verify(commentRepo).findVisible(any(), any(), hidden.capture(), any());
            assertThat(hidden.getValue()).containsExactly(blocked);
            verify(commentRepo).findVisible(org.mockito.ArgumentMatchers.eq(post.getId()),
                    org.mockito.ArgumentMatchers.eq(me), any(), any());
        }

        @Test
        void aCallerWithNoBlocksStillPassesAValueSoNotInIsNeverGivenAnEmptyList() {
            BlogPost post = postBy(other);
            when(commentRepo.findVisible(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

            service.list(post.getId(), firstPage);

            ArgumentCaptor<Collection<UUID>> hidden = ArgumentCaptor.forClass(Collection.class);
            verify(commentRepo).findVisible(any(), any(), hidden.capture(), any());
            assertThat(hidden.getValue()).containsExactly(new UUID(0, 0));
        }

        @Test
        void commentsAreOldestFirstWithTheIdBreakingTiesAndAPageIsAtMostFifty() {
            BlogPost post = postBy(other);
            when(commentRepo.findVisible(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

            service.list(post.getId(), PageRequest.of(3, 500, Sort.by("createdAt").descending()));

            ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
            verify(commentRepo).findVisible(any(), any(), any(), page.capture());
            assertThat(page.getValue().getPageNumber()).isEqualTo(3);
            assertThat(page.getValue().getPageSize()).isEqualTo(50);
            assertThat(page.getValue().getSort()).isEqualTo(Sort.by("createdAt", "id"));
        }

        @Test
        void authorsAreLookedUpOnceForThePageAndOwnCommentsCarryTheirStatus() {
            BlogPost post = postBy(other);
            BlogComment mineHidden = BlogComment.write(post.getId(), me, "mine", now);
            ReflectionTestUtils.setField(mineHidden, "status", BlogCommentStatus.HIDDEN);
            BlogComment theirs = BlogComment.write(post.getId(), other, "theirs", now);
            BlogComment theirsToo = BlogComment.write(post.getId(), other, "theirs too", now);
            BlogComment erased = BlogComment.write(post.getId(), null, "left behind", now);
            when(commentRepo.findVisible(any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(mineHidden, theirs, theirsToo, erased)));

            Page<BlogCommentResult> page = service.list(post.getId(), firstPage);

            verify(publicProfileReader, times(1)).profiles(Set.of(me, other));
            assertThat(page.getContent()).extracting(BlogCommentResult::status).containsExactly(
                    BlogCommentStatus.HIDDEN, BlogCommentStatus.VISIBLE, BlogCommentStatus.VISIBLE,
                    BlogCommentStatus.VISIBLE);
            assertThat(page.getContent()).extracting(BlogCommentResult::canEdit).containsExactly(false, false, false,
                    false);
            assertThat(page.getContent().get(3).author()).isNull();
            assertThat(page.getContent().get(1).author().userId()).isEqualTo(other);
        }

        @Test
        void aVisibleCommentOfMineCanBeEdited() {
            BlogPost post = postBy(other);
            BlogComment mine = BlogComment.write(post.getId(), me, "mine", now);
            when(commentRepo.findVisible(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(mine)));

            assertThat(service.list(post.getId(), firstPage).getContent().get(0).canEdit()).isTrue();
        }

        @Test
        void aPageOfNoCommentsAsksNobodyForProfiles() {
            BlogPost post = postBy(other);
            when(commentRepo.findVisible(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

            service.list(post.getId(), firstPage);

            verifyNoInteractions(publicProfileReader);
        }
    }

    @Nested
    class Creating {

        private final SaveCommentCommand command = new SaveCommentCommand("Nice post");

        @Test
        void aCommentIsSavedForTheCallerOnThePostAndCountsAgainstTheRateLimit() {
            BlogPost post = postBy(other);

            BlogCommentResult result = service.create(post.getId(), command);

            ArgumentCaptor<BlogComment> saved = ArgumentCaptor.forClass(BlogComment.class);
            verify(commentRepo).save(saved.capture());
            assertThat(saved.getValue().getPostId()).isEqualTo(post.getId());
            assertThat(saved.getValue().getAuthorId()).isEqualTo(me);
            assertThat(saved.getValue().getBody()).isEqualTo("Nice post");
            assertThat(saved.getValue().getCreatedAt()).isEqualTo(now);
            verify(rateLimiter).check(me, "blog-comment", 10, Duration.ofMinutes(1));
            assertThat(result.author().userId()).isEqualTo(me);
            assertThat(result.canEdit()).isTrue();
            assertThat(result.status()).isEqualTo(BlogCommentStatus.VISIBLE);
        }

        @Test
        void aPostThatIsNotOpenGetsNoComment() {
            BlogPost post = postBy(me);
            hide(post);

            assertThatThrownBy(() -> service.create(post.getId(), command)).isInstanceOfSatisfying(
                    ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_OPEN"));

            verify(commentRepo, never()).save(any());
        }

        @Test
        void aPostTheReadRuleRefusesGetsNoComment() {
            BlogPost post = postBy(other);
            when(readRule.canRead(post, me)).thenReturn(false);

            assertThatThrownBy(() -> service.create(post.getId(), command)).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

            verify(commentRepo, never()).save(any());
        }

        @Test
        void aPostThatDoesNotExistIsNotFound() {
            UUID missing = UUID.randomUUID();
            when(postRepo.findById(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(missing, command)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));
        }

        @Test
        void aBlockPlacedAfterTheReadRuleStillStopsTheComment() {
            BlogPost post = postBy(other);
            // Readable when the post is read, a block placed a moment later is found when the comment is about to be
            // written.
            when(readRule.isBlockedWith(other, me)).thenReturn(true);

            assertThatThrownBy(() -> service.create(post.getId(), command)).isInstanceOfSatisfying(
                    ForbiddenException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_INTERACTION_NOT_ALLOWED"));

            verify(commentRepo, never()).save(any());
        }
    }

    @Nested
    class Updating {

        private final SaveCommentCommand command = new SaveCommentCommand("Edited");

        @Test
        void theAuthorsCommentIsEditedInPlace() {
            BlogPost post = postBy(other);
            BlogComment comment = commentBy(me, post.getId());

            BlogCommentResult result = service.update(comment.getId(), command);

            assertThat(comment.getBody()).isEqualTo("Edited");
            assertThat(comment.getEditedAt()).isEqualTo(now);
            assertThat(result.body()).isEqualTo("Edited");
            assertThat(result.canEdit()).isTrue();
        }

        /**
         * What keeps an edit from writing 'VISIBLE' back over a moderator's hide: the first and only read of the
         * comment takes its row lock, before the post is asked about. The integration test shows it against a real
         * lock.
         */
        @Test
        void theCommentIsReadOnlyThroughTheLockingFinderAndBeforeThePost() {
            BlogPost post = postBy(other);
            BlogComment comment = commentBy(me, post.getId());

            service.update(comment.getId(), command);

            InOrder order = inOrder(commentRepo, postRepo);
            order.verify(commentRepo).findByIdForUpdate(comment.getId());
            order.verify(postRepo).findById(post.getId());
            verify(commentRepo, never()).findById(any());
        }

        @Test
        void aCommentThatDoesNotExistIsNotFound() {
            UUID missing = UUID.randomUUID();
            when(commentRepo.findByIdForUpdate(missing)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(missing, command)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_FOUND"));
        }

        @Test
        void aCommentOnAPostTheReadRuleNowRefusesIsNotFoundAndStaysAsItWas() {
            // The caller commented, and the post's read rule has since refused them (a block, or the post hidden).
            BlogPost post = postBy(other);
            BlogComment comment = commentBy(me, post.getId());
            when(readRule.canRead(post, me)).thenReturn(false);

            assertThatThrownBy(() -> service.update(comment.getId(), command)).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_FOUND"));

            assertThat(comment.getBody()).isEqualTo("A comment");
        }

        @Test
        void aCommentWhosePostIsGoneIsNotFound() {
            BlogComment comment = commentBy(me, UUID.randomUUID());
            when(postRepo.findById(comment.getPostId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(comment.getId(), command)).isInstanceOfSatisfying(
                    NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_FOUND"));
        }

        @Test
        void aCommentOnAPostThatIsNoLongerOpenStaysAsItWas() {
            BlogPost post = postBy(me);
            hide(post);
            BlogComment comment = commentBy(me, post.getId());

            assertThatThrownBy(() -> service.update(comment.getId(), command)).isInstanceOfSatisfying(
                    ConflictException.class, e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_OPEN"));

            assertThat(comment.getBody()).isEqualTo("A comment");
        }

        @Test
        void someoneElsesCommentIsLeftAlone() {
            BlogPost post = postBy(other);
            BlogComment comment = commentBy(other, post.getId());

            assertThatThrownBy(() -> service.update(comment.getId(), command)).isInstanceOf(ForbiddenException.class);

            assertThat(comment.getBody()).isEqualTo("A comment");
        }
    }

    @Nested
    class Deleting {

        /** A delete that raced a moderator's action waits for it instead of failing on a row that changed under it. */
        @Test
        void theCommentIsReadOnlyThroughTheLockingFinder() {
            BlogComment comment = commentBy(me, UUID.randomUUID());

            service.delete(comment.getId());

            verify(commentRepo).findByIdForUpdate(comment.getId());
            verify(commentRepo, never()).findById(any());
        }

        @Test
        void theAuthorsCommentIsDeleted() {
            BlogComment comment = commentBy(me, UUID.randomUUID());

            service.delete(comment.getId());

            verify(commentRepo).delete(comment);
        }

        @Test
        void aCommentIsDeletedWithoutLookingAtItsPostSoAHiddenPostDoesNotKeepItThere() {
            BlogComment comment = commentBy(me, UUID.randomUUID());

            service.delete(comment.getId());

            verifyNoInteractions(postRepo);
        }

        @Test
        void someoneElsesCommentIsNotDeleted() {
            BlogComment comment = commentBy(other, UUID.randomUUID());

            assertThatThrownBy(() -> service.delete(comment.getId())).isInstanceOf(ForbiddenException.class);

            verify(commentRepo, never()).delete(any());
        }

        @Test
        void aCommentThatDoesNotExistIsNotFound() {
            UUID missing = UUID.randomUUID();
            doReturn(Optional.empty()).when(commentRepo).findByIdForUpdate(missing);

            assertThatThrownBy(() -> service.delete(missing)).isInstanceOfSatisfying(NotFoundException.class,
                    e -> assertThat(e.getCode()).isEqualTo("BLOG_COMMENT_NOT_FOUND"));
        }
    }
}
