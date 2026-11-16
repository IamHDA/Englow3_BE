package com.englow3.blog.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.blog.dto.result.LikeStateResult;
import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.repository.BlogPostLikeRepository;
import com.englow3.blog.repository.BlogPostRepository;
import com.englow3.blog.service.BlogLikeService;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/** Likes are plain rows; what is decided here is only who may add one and what the caller is told afterwards. */
@Service
@RequiredArgsConstructor
public class BlogLikeServiceImpl implements BlogLikeService {

    /** A person likes well under this a minute; a script does not. */
    static final int MAX_LIKES_PER_WINDOW = 60;
    static final Duration LIKE_WINDOW = Duration.ofMinutes(1);

    private final UserDirectory userDirectory;
    private final BlogReadRule readRule;
    private final BlogPostRepository postRepo;
    private final BlogPostLikeRepository likeRepo;
    private final UserRateLimiter rateLimiter;
    private final Clock clock;

    @Override
    @Transactional
    public LikeStateResult like(UUID postId) {
        UUID me = userDirectory.requireCurrentUserId();
        rateLimiter.check(me, "blog-like", MAX_LIKES_PER_WINDOW, LIKE_WINDOW);

        BlogPost post = postRepo.findById(postId).filter(found -> readRule.canRead(found, me))
                .orElseThrow(() -> postNotFound(postId));
        post.ensureOpenForInteraction();

        likeRepo.insertIfAbsent(postId, me, Instant.now(clock));
        return new LikeStateResult(true, likeRepo.countByIdPostId(postId));
    }

    /**
     * Takes the like back from any post that exists, also one that has since been hidden or removed, or by someone who
     * has since blocked the caller: a like must always be removable. What the caller is told is another matter - the
     * count of a post they cannot read is not theirs to learn, so it is 0 there, the same as for a post nobody likes.
     */
    @Override
    @Transactional
    public LikeStateResult unlike(UUID postId) {
        UUID me = userDirectory.requireCurrentUserId();
        BlogPost post = postRepo.findById(postId).orElseThrow(() -> postNotFound(postId));
        likeRepo.deleteLike(postId, me);
        long likeCount = readRule.canRead(post, me) ? likeRepo.countByIdPostId(postId) : 0;
        return new LikeStateResult(false, likeCount);
    }

    private static NotFoundException postNotFound(UUID postId) {
        return new NotFoundException("BLOG_POST_NOT_FOUND", "No post with id %s".formatted(postId));
    }
}
