package com.englow3.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.englow3.blog.dto.projection.PostSummaryRow;
import com.englow3.blog.dto.projection.SavedPostsFilter;
import com.englow3.blog.dto.result.BlogPostSummaryResult;
import com.englow3.blog.dto.result.SaveStateResult;
import com.englow3.blog.entity.BlogPost;
import com.englow3.blog.entity.BlogPostStatus;
import com.englow3.blog.query.BlogPostListQuery;
import com.englow3.blog.repository.BlogPostRepository;
import com.englow3.blog.repository.BlogPostSaveRepository;
import com.englow3.blog.service.BlogSaveService;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.ratelimit.UserRateLimiter;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.UserDirectory;

/**
 * Orchestration only: who may add a save, that taking one back is always allowed, and what the saved list asks of the
 * query. That a hidden post cannot be saved is {@link BlogPost}'s rule and has its own test; the real entity is used
 * here, so what is checked is only that no save is written when it refuses.
 */
class BlogSaveServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final FriendshipReader friendshipReader = mock(FriendshipReader.class);
    private final BlogReadRule readRule = mock(BlogReadRule.class);
    private final BlogPostRepository postRepo = mock(BlogPostRepository.class);
    private final BlogPostSaveRepository saveRepo = mock(BlogPostSaveRepository.class);
    private final BlogPostListQuery listQuery = mock(BlogPostListQuery.class);
    private final PublicProfileReader publicProfileReader = mock(PublicProfileReader.class);
    /** Real: it only makes summaries of rows, through the two mocks it is given. */
    private final BlogPostSummaries summaries = new BlogPostSummaries(listQuery, publicProfileReader);
    private final UserRateLimiter rateLimiter = mock(UserRateLimiter.class);
    private final Instant now = Instant.parse("2026-11-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final BlogSaveService service = new BlogSaveServiceImpl(userDirectory, friendshipReader, readRule, postRepo,
            saveRepo, listQuery, summaries, rateLimiter, clock);

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

    private PostSummaryRow row(UUID author) {
        return new PostSummaryRow(UUID.randomUUID(), author, "Title", BlogPostStatus.PUBLISHED, "text", now, null, 3, 2,
                false, true);
    }

    @Test
    void savingInsertsTheSaveAtTheClockAndCountsAgainstTheRateLimit() {
        BlogPost post = postBy(other);

        SaveStateResult result = service.save(post.getId());

        verify(saveRepo).insertIfAbsent(post.getId(), me, now);
        verify(rateLimiter).check(me, "blog-save", 60, Duration.ofMinutes(1));
        assertThat(result).isEqualTo(new SaveStateResult(true));
    }

    @Test
    void aPostThatIsNotOpenIsNotSaved() {
        BlogPost post = postBy(me);
        hide(post);

        assertThatThrownBy(() -> service.save(post.getId())).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_OPEN"));

        verify(saveRepo, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void aPostTheReadRuleRefusesIsNotFoundAndIsNotSaved() {
        BlogPost post = postBy(other);
        when(readRule.canRead(post, me)).thenReturn(false);

        assertThatThrownBy(() -> service.save(post.getId())).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

        verify(saveRepo, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void aPostThatDoesNotExistIsNotFoundWhenSaved() {
        UUID missing = UUID.randomUUID();
        when(postRepo.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(missing)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

        verify(saveRepo, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void unsavingRemovesTheCallersSave() {
        BlogPost post = postBy(other);

        SaveStateResult result = service.unsave(post.getId());

        verify(saveRepo).deleteSave(post.getId(), me);
        assertThat(result).isEqualTo(new SaveStateResult(false));
    }

    /** A save must always be removable: what the post has become, or who blocked whom, is no reason to keep it. */
    @Test
    void aSaveIsTakenBackFromAPostTheCallerCanNoLongerRead() {
        BlogPost post = postBy(other);
        when(readRule.canRead(post, me)).thenReturn(false);

        SaveStateResult result = service.unsave(post.getId());

        verify(saveRepo).deleteSave(post.getId(), me);
        assertThat(result).isEqualTo(new SaveStateResult(false));
    }

    @Test
    void unsavingAPostThatDoesNotExistIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(postRepo.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unsave(missing)).isInstanceOfSatisfying(NotFoundException.class,
                e -> assertThat(e.getCode()).isEqualTo("BLOG_POST_NOT_FOUND"));

        verify(saveRepo, never()).deleteSave(any(), any());
    }

    // --- the saved list --------------------------------------------------------------------------------------------

    private SavedPostsFilter filterPassedToTheQuery() {
        ArgumentCaptor<SavedPostsFilter> filter = ArgumentCaptor.forClass(SavedPostsFilter.class);
        verify(listQuery).listSaved(filter.capture());
        return filter.getValue();
    }

    @Test
    void theSavedListAsksForTheCallersSavesTheirBlocksAndTheRequestedPage() {
        UUID blocked = UUID.randomUUID();
        when(friendshipReader.blockedEitherWay(me)).thenReturn(Set.of(blocked));

        service.listSaved(PageRequest.of(2, 10));

        SavedPostsFilter filter = filterPassedToTheQuery();
        assertThat(filter.viewerId()).isEqualTo(me);
        assertThat(filter.hiddenAuthors()).containsExactly(blocked);
        assertThat(filter.limit()).isEqualTo(10);
        assertThat(filter.offset()).isEqualTo(20);
    }

    @Test
    void aSavedPageLargerThanFiftyIsCutToFifty() {
        service.listSaved(PageRequest.of(0, 500));

        assertThat(filterPassedToTheQuery().limit()).isEqualTo(50);
    }

    @Test
    void theSavedListIsMadeOfSummariesWithBlocksProfilesAndTagsAskedOncePerPage() {
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();
        PostSummaryRow one = row(authorA);
        PostSummaryRow two = row(authorB);
        PostSummaryRow three = row(authorA);
        when(listQuery.listSaved(any())).thenReturn(List.of(one, two, three));

        Page<BlogPostSummaryResult> page = service.listSaved(PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(BlogPostSummaryResult::id).containsExactly(one.id(), two.id(),
                three.id());
        assertThat(page.getContent()).allMatch(BlogPostSummaryResult::savedByMe);
        verify(friendshipReader, times(1)).blockedEitherWay(me);
        verify(publicProfileReader, times(1)).profiles(Set.of(authorA, authorB));
        verify(listQuery, times(1)).tagsOf(Set.of(one.id(), two.id(), three.id()));
    }

    @Test
    void aFullSavedPageReportsTheTotalTheQueryCounts() {
        when(listQuery.listSaved(any())).thenReturn(List.of(row(other), row(other)));
        when(listQuery.countSaved(any())).thenReturn(41L);

        Page<BlogPostSummaryResult> page = service.listSaved(PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(41);
        assertThat(page.getTotalPages()).isEqualTo(21);
    }

    @Test
    void anEmptySavedListAsksNobodyForProfiles() {
        when(listQuery.listSaved(any())).thenReturn(List.of());

        Page<BlogPostSummaryResult> page = service.listSaved(PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verifyNoInteractions(publicProfileReader);
    }
}
