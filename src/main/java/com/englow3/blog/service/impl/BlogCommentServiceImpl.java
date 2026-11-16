package com.englow3.blog.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.blog.dto.command.SaveCommentCommand;
import com.englow3.blog.dto.result.BlogAuthorResult;
import com.englow3.blog.dto.result.BlogCommentResult;
import com.englow3.blog.entity.BlogComment;
import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.repository.BlogCommentRepository;
import com.englow3.blog.repository.BlogPostRepository;
import com.englow3.blog.service.BlogCommentService;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.ForbiddenException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.PublicProfileReader.PublicProfile;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * Comments under the post's own read rule: if the caller cannot read the post, they cannot list, write or edit comments
 * on it. The rules about a comment itself live in {@link BlogComment}.
 */
@Service
@RequiredArgsConstructor
public class BlogCommentServiceImpl implements BlogCommentService {

    /** A person writes well under this a minute; a script or a stuck retry loop does not. */
    static final int MAX_COMMENTS_PER_WINDOW = 10;
    static final Duration COMMENT_WINDOW = Duration.ofMinutes(1);

    static final int MAX_PAGE_SIZE = 50;

    /**
     * {@code not in} needs a value to compare with, and a person with no blocks has none to give: this id belongs to no
     * account, so a list with it excludes nobody. Chosen over a second query for the unblocked case, which would be two
     * statements to keep in step.
     */
    private static final UUID NOBODY = new UUID(0, 0);

    private final UserDirectory userDirectory;
    private final FriendshipReader friendshipReader;
    private final PublicProfileReader publicProfileReader;
    private final BlogPostRepository postRepo;
    private final BlogCommentRepository commentRepo;
    private final UserRateLimiter rateLimiter;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Page<BlogCommentResult> list(UUID postId, Pageable page) {
        UUID me = userDirectory.requireCurrentUserId();
        postRepo.findById(postId).filter(post -> canRead(post, me)).orElseThrow(() -> postNotFound(postId));

        Set<UUID> hidden = friendshipReader.blockedEitherWay(me);
        Collection<UUID> hiddenOrNobody = hidden.isEmpty() ? Set.of(NOBODY) : hidden;
        Pageable oldestFirst = PageRequest.of(page.getPageNumber(),
                Math.max(1, Math.min(page.getPageSize(), MAX_PAGE_SIZE)), Sort.by("createdAt", "id"));
        Page<BlogComment> comments = commentRepo.findVisible(postId, me, hiddenOrNobody, oldestFirst);

        // One call for the whole page. A null author is a person who erased their account: no profile to look up.
        Set<UUID> authorIds = comments.stream().map(BlogComment::getAuthorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, PublicProfile> profiles = authorIds.isEmpty() ? Map.of() : publicProfileReader.profiles(authorIds);
        return comments.map(comment -> result(comment, profiles, me));
    }

    @Override
    @Transactional
    public BlogCommentResult create(UUID postId, SaveCommentCommand command) {
        UUID me = userDirectory.requireCurrentUserId();
        rateLimiter.check(me, "blog-comment", MAX_COMMENTS_PER_WINDOW, COMMENT_WINDOW);

        BlogPost post = postRepo.findById(postId).filter(found -> canRead(found, me))
                .orElseThrow(() -> postNotFound(postId));
        post.ensureOpenForInteraction();
        // The read rule above already answers "not found" for a block, so this is for a block placed a moment later;
        // it is asked again, not remembered, because the comment is about to be written.
        if (post.getAuthorId() != null && friendshipReader.isBlockedEitherWay(me, post.getAuthorId())) {
            throw new ForbiddenException("BLOG_INTERACTION_NOT_ALLOWED", "You cannot comment on this post");
        }

        BlogComment comment = commentRepo.save(BlogComment.write(postId, me, command.body(), Instant.now(clock)));
        return result(comment, publicProfileReader.profiles(Set.of(me)), me);
    }

    @Override
    @Transactional
    public BlogCommentResult update(UUID commentId, SaveCommentCommand command) {
        UUID me = userDirectory.requireCurrentUserId();
        BlogComment comment = commentRepo.findById(commentId).orElseThrow(() -> commentNotFound(commentId));
        // The post's read rule applies to editing too: someone who has since been blocked by the post's author, or
        // whose
        // post was hidden, cannot keep editing in a place they can no longer see. The answer is the comment's, not the
        // post's: the comment is what was asked for.
        BlogPost post = postRepo.findById(comment.getPostId()).filter(found -> canRead(found, me))
                .orElseThrow(() -> commentNotFound(commentId));
        post.ensureOpenForInteraction();

        comment.edit(me, command.body(), Instant.now(clock));
        return result(comment, publicProfileReader.profiles(Set.of(me)), me);
    }

    @Override
    @Transactional
    public void delete(UUID commentId) {
        UUID me = userDirectory.requireCurrentUserId();
        BlogComment comment = commentRepo.findById(commentId).orElseThrow(() -> commentNotFound(commentId));
        comment.ensureDeletableBy(me);
        commentRepo.delete(comment);
    }

    /** The read rule every blog use case shares; see {@code BlogLikeServiceImpl#canRead}. */
    private boolean canRead(BlogPost post, UUID me) {
        return post.isReadableBy(me) && (post.getAuthorId() == null || post.isAuthor(me)
                || !friendshipReader.isBlockedEitherWay(me, post.getAuthorId()));
    }

    private static BlogCommentResult result(BlogComment comment, Map<UUID, PublicProfile> profiles, UUID me) {
        PublicProfile profile = comment.getAuthorId() == null ? null : profiles.get(comment.getAuthorId());
        BlogAuthorResult author = profile == null ? null
                : new BlogAuthorResult(profile.id(), profile.displayName(), profile.avatarUrl());
        return new BlogCommentResult(comment.getId(), comment.getPostId(), author, comment.getBody(),
                comment.getStatus(), comment.isEditableBy(me), comment.getCreatedAt(), comment.getEditedAt());
    }

    private static NotFoundException postNotFound(UUID postId) {
        return new NotFoundException("BLOG_POST_NOT_FOUND", "No post with id %s".formatted(postId));
    }

    private static NotFoundException commentNotFound(UUID commentId) {
        return new NotFoundException("BLOG_COMMENT_NOT_FOUND", "No comment with id %s".formatted(commentId));
    }
}
