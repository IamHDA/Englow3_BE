package com.englow3.blog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

import com.englow3.blog.dto.projection.PostSummaryRow;
import com.englow3.blog.dto.projection.PostTagRow;
import com.englow3.blog.dto.projection.RecommendationCandidate;
import com.englow3.blog.dto.projection.RecommendationFilter;
import com.englow3.blog.dto.result.BlogRecommendationResult;
import com.englow3.blog.dto.result.BlogTagResult;
import com.englow3.blog.entity.BlogPostStatus;
import com.englow3.blog.entity.BlogRecommendationKind;
import com.englow3.blog.entity.BlogTagKind;
import com.englow3.blog.query.BlogPostListQuery;
import com.englow3.blog.query.BlogRecommendationQuery;
import com.englow3.blog.service.BlogRecommendationService;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.user.api.LearnerGoalReader;
import com.englow3.user.api.LearnerGoalReader.Goal;
import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.api.UserDirectory;

/**
 * Orchestration only: which questions are asked, of whom, over how long a time, and when the answer of the profile is
 * given up for the week's favourites. What a post is worth to a reader, and the order of the best, are
 * {@code BlogRecommendationScore}'s and have their own test; here the posts are only made to fit or not to fit.
 */
class BlogRecommendationServiceTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final FriendshipReader friendshipReader = mock(FriendshipReader.class);
    private final LearnerGoalReader goalReader = mock(LearnerGoalReader.class);
    private final BlogRecommendationQuery recommendationQuery = mock(BlogRecommendationQuery.class);
    private final BlogPostListQuery listQuery = mock(BlogPostListQuery.class);
    private final PublicProfileReader publicProfileReader = mock(PublicProfileReader.class);
    /** Real: it only makes summaries of rows, through the two mocks it is given. */
    private final BlogPostSummaries summaries = new BlogPostSummaries(listQuery, publicProfileReader);
    private final Instant now = Instant.parse("2026-11-16T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final BlogRecommendationService service = new BlogRecommendationServiceImpl(userDirectory, friendshipReader,
            goalReader, recommendationQuery, listQuery, summaries, clock);

    private final UUID me = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(userDirectory.requireCurrentUserId()).thenReturn(me);
    }

    private static Goal goal(String level, String certificate, String... skills) {
        return new Goal(UUID.randomUUID(), level, certificate != null, certificate, BigDecimal.ZERO, BigDecimal.ONE,
                null, null, 30, List.of(), List.of(skills));
    }

    private void declared(Goal goal) {
        when(goalReader.read(me)).thenReturn(Optional.of(goal));
    }

    /** A candidate that fits a reader aiming for IELTS: it carries that tag. */
    private RecommendationCandidate ieltsPost(long likes) {
        return new RecommendationCandidate(UUID.randomUUID(), now.minus(Duration.ofDays(3)), likes);
    }

    private PostSummaryRow row(UUID id) {
        return new PostSummaryRow(id, author, "Title", BlogPostStatus.PUBLISHED, "text", now, null, 3, 2, false, false);
    }

    /** What the tag query returns for every candidate the test names: all of them are IELTS posts. */
    private void areAllIelts(List<RecommendationCandidate> candidates) {
        List<PostTagRow> tags = candidates.stream()
                .map(candidate -> new PostTagRow(candidate.postId(), BlogTagKind.CERTIFICATE, "IELTS")).toList();
        when(listQuery.tagsOf(any())).thenReturn(tags);
    }

    private void rowsExist(List<UUID> ids) {
        when(listQuery.rowsOf(any(), eq(me))).thenReturn(ids.stream().map(this::row).toList());
    }

    private RecommendationFilter filterPassedToTheCandidateQuery() {
        ArgumentCaptor<RecommendationFilter> filter = ArgumentCaptor.forClass(RecommendationFilter.class);
        verify(recommendationQuery).candidates(filter.capture(), any(), anyInt());
        return filter.getValue();
    }

    @Test
    void postsThatFitAreRecommendedWithTheTagsThatMatchedAndNothingElseIsAsked() {
        declared(goal("B1", "IELTS"));
        List<RecommendationCandidate> candidates = List.of(ieltsPost(1), ieltsPost(2));
        when(recommendationQuery.candidates(any(), any(), anyInt())).thenReturn(candidates);
        areAllIelts(candidates);
        rowsExist(candidates.stream().map(RecommendationCandidate::postId).toList());

        List<BlogRecommendationResult> results = service.recommend(3);

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.kind()).isEqualTo(BlogRecommendationKind.PROFILE);
            assertThat(result.matchedTags()).containsExactly(new BlogTagResult(BlogTagKind.CERTIFICATE, "IELTS"));
        });
        verify(recommendationQuery, never()).likedSince(any(), any(), anyInt());
    }

    @Test
    void candidatesAreAskedForOverTheLastNinetyDaysWithTheCallersBlocksAndCapped() {
        UUID blocked = UUID.randomUUID();
        when(friendshipReader.blockedEitherWay(me)).thenReturn(Set.of(blocked));
        declared(goal("B1", "IELTS"));

        service.recommend(3);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Integer> cap = ArgumentCaptor.forClass(Integer.class);
        verify(recommendationQuery).candidates(any(), since.capture(), cap.capture());
        assertThat(since.getValue()).isEqualTo(now.minus(Duration.ofDays(90)));
        assertThat(cap.getValue()).isEqualTo(500);
        RecommendationFilter filter = filterPassedToTheCandidateQuery();
        assertThat(filter.viewerId()).isEqualTo(me);
        assertThat(filter.hiddenAuthors()).containsExactly(blocked);
        verify(friendshipReader, times(1)).blockedEitherWay(me);
    }

    @Test
    void onlyTheFirstLimitOfThePostsThatFitAreReturned() {
        declared(goal("B1", "IELTS"));
        List<RecommendationCandidate> candidates = List.of(ieltsPost(4), ieltsPost(3), ieltsPost(2), ieltsPost(1));
        when(recommendationQuery.candidates(any(), any(), anyInt())).thenReturn(candidates);
        areAllIelts(candidates);
        rowsExist(candidates.stream().map(RecommendationCandidate::postId).toList());

        assertThat(service.recommend(2)).hasSize(2);
    }

    @Test
    void whenNothingFitsEnoughTheWeeksMostLikedPostsAreRecommendedInstead() {
        declared(goal("B1", "IELTS"));
        when(recommendationQuery.candidates(any(), any(), anyInt()))
                .thenReturn(List.of(new RecommendationCandidate(UUID.randomUUID(), now, 5)));
        UUID popular = UUID.randomUUID();
        when(recommendationQuery.likedSince(any(), any(), anyInt())).thenReturn(List.of(popular));
        rowsExist(List.of(popular));

        List<BlogRecommendationResult> results = service.recommend(3);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.post().id()).isEqualTo(popular);
            assertThat(result.kind()).isEqualTo(BlogRecommendationKind.POPULAR_THIS_WEEK);
            assertThat(result.matchedTags()).isEmpty();
        });
        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(recommendationQuery).likedSince(any(), since.capture(), eq(3));
        assertThat(since.getValue()).isEqualTo(now.minus(Duration.ofDays(7)));
    }

    @Test
    void someoneWithoutALearnerProfileGetsTheWeeksMostLikedWithoutAnyCandidateBeingRead() {
        when(goalReader.read(me)).thenReturn(Optional.empty());
        UUID popular = UUID.randomUUID();
        when(recommendationQuery.likedSince(any(), any(), anyInt())).thenReturn(List.of(popular));
        rowsExist(List.of(popular));

        List<BlogRecommendationResult> results = service.recommend(3);

        assertThat(results).extracting(BlogRecommendationResult::kind)
                .containsExactly(BlogRecommendationKind.POPULAR_THIS_WEEK);
        verify(recommendationQuery, never()).candidates(any(), any(), anyInt());
    }

    @Test
    void aProfileThatDeclaresNothingBlogCanUseIsTreatedAsNoProfile() {
        declared(goal(null, null, "PRONUNCIATION"));

        service.recommend(3);

        verify(recommendationQuery, never()).candidates(any(), any(), anyInt());
        verify(recommendationQuery).likedSince(any(), any(), anyInt());
    }

    @Test
    void whenThereIsNothingToRecommendTheListIsEmptyAndNoPostIsRead() {
        declared(goal("B1", "IELTS"));

        assertThat(service.recommend(3)).isEmpty();

        verify(listQuery, never()).rowsOf(any(), any());
        verifyNoInteractions(publicProfileReader);
    }

    @Test
    void theLimitIsKeptBetweenOneAndTen() {
        when(goalReader.read(me)).thenReturn(Optional.empty());

        service.recommend(50);
        service.recommend(0);

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        verify(recommendationQuery, times(2)).likedSince(any(), any(), limit.capture());
        assertThat(limit.getAllValues()).containsExactly(10, 1);
    }

    @Test
    void aChosenPostThatIsGoneByTheTimeItsRowIsReadIsLeftOut() {
        when(goalReader.read(me)).thenReturn(Optional.empty());
        UUID there = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        when(recommendationQuery.likedSince(any(), any(), anyInt())).thenReturn(List.of(gone, there));
        rowsExist(List.of(there));

        assertThat(service.recommend(3)).extracting(result -> result.post().id()).containsExactly(there);
    }
}
