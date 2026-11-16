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
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/** Likes are plain rows; what is decided here is only who may add one. */
@Service
@RequiredArgsConstructor
public class BlogLikeServiceImpl implements BlogLikeService {

    /** A person likes well under this a minute; a script does not. */
    static final int MAX_LIKES_PER_WINDOW = 60;
    static final Duration LIKE_WINDOW = Duration.ofMinutes(1);

    private final UserDirectory userDirectory;
    private final FriendshipReader friendshipReader;
    private final BlogPostRepository postRepo;
    private final BlogPostLikeRepository likeRepo;
    private final UserRateLimiter rateLimiter;
    private final Clock clock;

    @Override
    @Transactional
    public LikeStateResult like(UUID postId) {
        UUID me = userDirectory.requireCurrentUserId();
        rateLimiter.check(me, "blog-like", MAX_LIKES_PER_WINDOW, LIKE_WINDOW);

        BlogPost post = postRepo.findById(postId).filter(found -> canRead(found, me))
                .orElseThrow(() -> postNotFound(postId));
        post.ensureOpenForInteraction();

        likeRepo.insertIfAbsent(postId, me, Instant.now(clock));
        return new LikeStateResult(true, likeRepo.countByIdPostId(postId));
    }

    /** Also on a post that has since been hidden or removed: taking a like back is never refused for that. */
    @Override
    @Transactional
    public LikeStateResult unlike(UUID postId) {
        UUID me = userDirectory.requireCurrentUserId();
        if (!postRepo.existsById(postId)) {
            throw postNotFound(postId);
        }
        likeRepo.deleteLike(postId, me);
        return new LikeStateResult(false, likeRepo.countByIdPostId(postId));
    }

    /**
     * The read rule every blog use case shares: a post the caller may not read does not exist for them. Hidden or
     * removed posts are read only by their author, and a post by someone the caller has a block with, either way, is
     * not read at all.
     */
    private boolean canRead(BlogPost post, UUID me) {
        return post.isReadableBy(me) && (post.getAuthorId() == null || post.isAuthor(me)
                || !friendshipReader.isBlockedEitherWay(me, post.getAuthorId()));
    }

    private static NotFoundException postNotFound(UUID postId) {
        return new NotFoundException("BLOG_POST_NOT_FOUND", "No post with id %s".formatted(postId));
    }
}
