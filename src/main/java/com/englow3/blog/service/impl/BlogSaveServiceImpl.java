package com.englow3.blog.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.blog.dto.projection.SavedPostsFilter;
import com.englow3.blog.dto.result.BlogPostSummaryResult;
import com.englow3.blog.dto.result.SaveStateResult;
import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.query.BlogPostListQuery;
import com.englow3.blog.repository.BlogPostRepository;
import com.englow3.blog.repository.BlogPostSaveRepository;
import com.englow3.blog.service.BlogSaveService;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/** Saves are plain rows; what is decided here is only who may add one and what the saved list holds. */
@Service
@RequiredArgsConstructor
public class BlogSaveServiceImpl implements BlogSaveService {

    /** A person saves well under this a minute; a script does not. */
    static final int MAX_SAVES_PER_WINDOW = 60;
    static final Duration SAVE_WINDOW = Duration.ofMinutes(1);

    private final UserDirectory userDirectory;
    private final FriendshipReader friendshipReader;
    private final BlogReadRule readRule;
    private final BlogPostRepository postRepo;
    private final BlogPostSaveRepository saveRepo;
    private final BlogPostListQuery listQuery;
    private final BlogPostSummaries summaries;
    private final UserRateLimiter rateLimiter;
    private final Clock clock;

    @Override
    @Transactional
    public SaveStateResult save(UUID postId) {
        UUID me = userDirectory.requireCurrentUserId();
        rateLimiter.check(me, "blog-save", MAX_SAVES_PER_WINDOW, SAVE_WINDOW);

        BlogPost post = postRepo.findById(postId).filter(found -> readRule.canRead(found, me))
                .orElseThrow(() -> postNotFound(postId));
        post.ensureOpenForInteraction();

        saveRepo.insertIfAbsent(postId, me, Instant.now(clock));
        return new SaveStateResult(true);
    }

    /**
     * Takes the save back from any post that exists, whatever it has become since and whoever the author has blocked: a
     * save must always be removable, the same as a like.
     */
    @Override
    @Transactional
    public SaveStateResult unsave(UUID postId) {
        UUID me = userDirectory.requireCurrentUserId();
        postRepo.findById(postId).orElseThrow(() -> postNotFound(postId));
        saveRepo.deleteSave(postId, me);
        return new SaveStateResult(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BlogPostSummaryResult> listSaved(Pageable page) {
        UUID me = userDirectory.requireCurrentUserId();
        Set<UUID> hidden = friendshipReader.blockedEitherWay(me);

        int size = Math.max(1, Math.min(page.getPageSize(), BlogPostServiceImpl.MAX_PAGE_SIZE));
        Pageable pageable = PageRequest.of(page.getPageNumber(), size);
        // An offset past what an int can hold is past any table: the empty page it gets is the right answer.
        int offset = (int) Math.min(pageable.getOffset(), Integer.MAX_VALUE);
        SavedPostsFilter filter = new SavedPostsFilter(me, hidden, size, offset);

        List<BlogPostSummaryResult> content = summaries.of(listQuery.listSaved(filter));
        return PageableExecutionUtils.getPage(content, pageable, () -> listQuery.countSaved(filter));
    }

    private static NotFoundException postNotFound(UUID postId) {
        return new NotFoundException("BLOG_POST_NOT_FOUND", "No post with id %s".formatted(postId));
    }
}
