package com.englow3.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.blog.dto.result.LikeStateResult;
import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.entity.BlogPostStatus;
import com.englow3.blog.repository.BlogPostLikeRepository;
import com.englow3.blog.repository.BlogPostRepository;
import com.englow3.blog.service.BlogLikeService;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.user.api.UserDirectory;

class BlogLikeServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final BlogReadRule readRule = mock(BlogReadRule.class);
    private final BlogPostRepository postRepo = mock(BlogPostRepository.class);
    private final BlogPostLikeRepository likeRepo = mock(BlogPostLikeRepository.class);
    private final UserRateLimiter rateLimiter = mock(UserRateLimiter.class);
    private final Instant now = Instant.parse("2026-11-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final BlogLikeService service = new BlogLikeServiceImpl(userDirectory, readRule, postRepo, likeRepo,
            rateLimiter, clock);

    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
        // Whether a post may be read is BlogReadRule's to answer and has its own test; here it is open unless a test
        // says the rule refuses.
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

    @Test
    void likingInsertsTheLikeAndReturnsTheCountAndCountsAgainstTheRateLimit() {
        BlogPost post = postBy(other);
        when(likeRepo.countByIdPostId(post.getId())).thenReturn(8L);

        LikeStateResult result = service.like(post.getId());

        verify(likeRepo).insertIfAbsent(post.getId(), me, now);
        verify(rateLimiter).check(me, "blog-like", 60, Duration.ofMinutes(1));
        assertThat(result).isEqualTo(new LikeStateResult(true, 8));
    }

    @Test
    void aPostThatIsNotOpenCannotBeLiked() {
        BlogPost post = postBy(me);
        hide(post);

        assertThatThrownBy(() -> service.like(post.getId())).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_OPEN"));

        verify(likeRepo, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void aPostTheReadRuleRefusesIsNotFoundAndGetsNoLike() {
        BlogPost post = postBy(other);
        when(readRule.canRead(post, me)).thenReturn(false);

        assertThatThrownBy(() -> service.like(post.getId())).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

        verify(likeRepo, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void aPostThatDoesNotExistIsNotFoundWhenLiked() {
        UUID missing = UUID.randomUUID();
        when(postRepo.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.like(missing)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));
    }

    @Test
    void unlikingRemovesTheCallersLikeAndReturnsTheCount() {
        BlogPost post = postBy(other);
        when(likeRepo.countByIdPostId(post.getId())).thenReturn(7L);

        LikeStateResult result = service.unlike(post.getId());

        verify(likeRepo).deleteLike(post.getId(), me);
        assertThat(result).isEqualTo(new LikeStateResult(false, 7));
    }

    /**
     * The like goes whatever the post has become; what the caller is told is 0, because the count of a post they cannot
     * read is not theirs to see.
     */
    @Test
    void aLikeIsTakenBackFromAPostTheCallerCanNoLongerReadAndTheCountReportedIsZero() {
        BlogPost post = postBy(other);
        when(readRule.canRead(post, me)).thenReturn(false);
        when(likeRepo.countByIdPostId(post.getId())).thenReturn(41L);

        LikeStateResult result = service.unlike(post.getId());

        verify(likeRepo).deleteLike(post.getId(), me);
        verify(likeRepo, never()).countByIdPostId(any());
        assertThat(result).isEqualTo(new LikeStateResult(false, 0));
    }

    @Test
    void unlikingAPostThatDoesNotExistIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(postRepo.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlike(missing)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

        verify(likeRepo, never()).deleteLike(any(), any());
    }
}
