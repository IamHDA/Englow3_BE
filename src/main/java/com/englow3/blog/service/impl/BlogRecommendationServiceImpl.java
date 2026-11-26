package com.englow3.blog.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.blog.dto.projection.PostSummaryRow;
import com.englow3.blog.dto.projection.PostTagRow;
import com.englow3.blog.dto.projection.RecommendationCandidate;
import com.englow3.blog.dto.projection.RecommendationFilter;
import com.englow3.blog.dto.result.BlogPostSummaryResult;
import com.englow3.blog.dto.result.BlogRecommendationResult;
import com.englow3.blog.dto.result.BlogTagResult;
import com.englow3.blog.entity.BlogRecommendationKind;
import com.englow3.blog.helper.BlogRecommendationScore;
import com.englow3.blog.helper.BlogRecommendationScore.Candidate;
import com.englow3.blog.helper.BlogRecommendationScore.Pick;
import com.englow3.blog.helper.BlogRecommendationScore.Profile;
import com.englow3.blog.query.BlogPostListQuery;
import com.englow3.blog.query.BlogRecommendationQuery;
import com.englow3.blog.service.BlogRecommendationService;
import com.englow3.friendship.api.FriendshipReader;
import com.englow3.user.api.LearnerGoalReader;
import com.englow3.user.api.UserDirectory;

import lombok.RequiredArgsConstructor;

/**
 * Reads what the caller declared, asks for the posts that may be recommended and lets {@link BlogRecommendationScore}
 * weigh them. When fewer fit than were asked for, the rest of the list is filled with what readers liked this week, and
 * then with the newest posts, so the list is only short when there is nothing left to show. How a post is weighed is
 * the helper's; what is here is the order of the questions and when to move on to the next.
 */
@Service
@RequiredArgsConstructor
public class BlogRecommendationServiceImpl implements BlogRecommendationService {

    static final int MAX_LIMIT = 10;

    /** Older posts are not recommended by fit: what was written last quarter is what is still worth reading. */
    static final Duration PROFILE_WINDOW = Duration.ofDays(90);
    /** Likes given in this long count towards "liked this week". */
    static final Duration POPULAR_WINDOW = Duration.ofDays(7);
    /** Every candidate is weighed in memory; this bounds it. The oldest in the window are the ones left out. */
    static final int MAX_CANDIDATES = 500;

    private final UserDirectory userDirectory;
    private final FriendshipReader friendshipReader;
    private final LearnerGoalReader goalReader;
    private final BlogRecommendationQuery recommendationQuery;
    private final BlogPostListQuery listQuery;
    private final BlogPostSummaries summaries;
    private final Clock clock;

    private record Chosen(UUID postId, BlogRecommendationKind kind, List<BlogTagResult> matchedTags) {
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlogRecommendationResult> recommend(int limit) {
        UUID me = userDirectory.requireCurrentUserId();
        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        Instant now = Instant.now(clock);
        RecommendationFilter filter = new RecommendationFilter(me, friendshipReader.blockedEitherWay(me));

        List<Chosen> chosen = new ArrayList<>(byProfile(me, filter, now, size));
        if (chosen.size() < size) {
            fill(chosen, recommendationQuery.likedSince(filter, now.minus(POPULAR_WINDOW), size),
                    BlogRecommendationKind.POPULAR_THIS_WEEK, size);
        }
        if (chosen.size() < size) {
            fill(chosen, recommendationQuery.latest(filter, size), BlogRecommendationKind.LATEST, size);
        }
        return results(me, chosen);
    }

    /**
     * Adds the posts not chosen yet, in their order, until there are {@code size}. Asking each source for {@code size}
     * is enough: at most {@code chosen.size()} of its answer can be chosen already, which leaves at least the gap.
     */
    private static void fill(List<Chosen> chosen, List<UUID> postIds, BlogRecommendationKind kind, int size) {
        Set<UUID> taken = chosen.stream().map(Chosen::postId).collect(Collectors.toSet());
        for (UUID postId : postIds) {
            if (chosen.size() >= size) {
                return;
            }
            if (taken.add(postId)) {
                chosen.add(new Chosen(postId, kind, List.of()));
            }
        }
    }

    private List<Chosen> byProfile(UUID me, RecommendationFilter filter, Instant now, int size) {
        Optional<Profile> profile = goalReader.read(me).map(goal -> BlogRecommendationScore
                .profileOf(goal.currentLevel(), goal.targetCertificate(), goal.targetSkills()))
                .filter(declared -> !declared.isEmpty());
        if (profile.isEmpty()) {
            return List.of();
        }

        List<RecommendationCandidate> candidates = recommendationQuery.candidates(filter, now.minus(PROFILE_WINDOW),
                MAX_CANDIDATES);
        Map<UUID, List<BlogTagResult>> tags = listQuery
                .tagsOf(candidates.stream().map(RecommendationCandidate::postId).collect(Collectors.toSet())).stream()
                .collect(Collectors.groupingBy(PostTagRow::postId,
                        Collectors.mapping(row -> new BlogTagResult(row.kind(), row.value()), Collectors.toList())));
        List<Candidate> weighed = candidates.stream().map(candidate -> new Candidate(candidate.postId(),
                candidate.createdAt(), candidate.likeCount(), tags.getOrDefault(candidate.postId(), List.of())))
                .toList();

        return BlogRecommendationScore.rank(profile.get(), weighed, size).stream()
                .map((Pick pick) -> new Chosen(pick.postId(), BlogRecommendationKind.PROFILE, pick.matchedTags()))
                .toList();
    }

    /** The summaries of the chosen posts, in the order they were chosen; one that has vanished since is skipped. */
    private List<BlogRecommendationResult> results(UUID me, List<Chosen> chosen) {
        if (chosen.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = chosen.stream().map(Chosen::postId).collect(Collectors.toSet());
        List<PostSummaryRow> rows = listQuery.rowsOf(ids, me);
        Map<UUID, BlogPostSummaryResult> posts = summaries.of(rows).stream()
                .collect(Collectors.toMap(BlogPostSummaryResult::id, Function.identity()));
        return chosen.stream().filter(pick -> posts.containsKey(pick.postId()))
                .map(pick -> new BlogRecommendationResult(posts.get(pick.postId()), pick.kind(), pick.matchedTags()))
                .toList();
    }
}
