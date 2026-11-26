package com.englow3.blog.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.englow3.blog.dto.projection.RecommendationCandidate;
import com.englow3.blog.dto.projection.RecommendationFilter;
import com.englow3.blog.entity.BlogPostStatus;
import com.englow3.shared.persistence.SqlTime;

import lombok.RequiredArgsConstructor;

/**
 * The posts that may be recommended to a viewer, before any of them is weighed: published, by someone else, neither
 * saved nor liked by the viewer - they have found it already - and not by anyone they have a block with. Reads only,
 * bound values only, like {@link BlogPostListQuery}. What a post is worth to a reader is decided by the caller; this
 * only hands over the posts that are eligible, and the order of the popular ones.
 */
@Component
@RequiredArgsConstructor
public class BlogRecommendationQuery {

    private final JdbcClient jdbcClient;

    private record Eligible(String sql, Map<String, Object> params) {
    }

    /**
     * The newest eligible posts posted since {@code since}, at most {@code limit}, with their like counts. Capped,
     * because the caller weighs every one of them: past the cap the oldest in the window are the ones that fall out.
     */
    public List<RecommendationCandidate> candidates(RecommendationFilter filter, Instant since, int limit) {
        Eligible eligible = eligible(filter);
        Map<String, Object> params = new HashMap<>(eligible.params());
        params.put("since", SqlTime.at(since));
        params.put("limit", limit);
        return jdbcClient
                .sql("""
                        select p.id, p.created_at,
                               (select count(*) from blog_post_likes l where l.post_id = p.id) as like_count
                          from blog_posts p
                         where p.created_at >= :since and""" + eligible.sql()
                        + " order by p.created_at desc, p.id limit :limit")
                .params(params).query((rs, rowNum) -> new RecommendationCandidate(rs.getObject("id", UUID.class),
                        SqlTime.read(rs, "created_at"), rs.getLong("like_count")))
                .list();
    }

    /**
     * The eligible posts that were liked since {@code since}, most liked in that time first - a like counts by the day
     * it was given, not the day the post was written - then the newer post, at most {@code limit}. A post nobody liked
     * in the time is not in the answer.
     */
    public List<UUID> likedSince(RecommendationFilter filter, Instant since, int limit) {
        Eligible eligible = eligible(filter);
        Map<String, Object> params = new HashMap<>(eligible.params());
        params.put("since", SqlTime.at(since));
        params.put("limit", limit);
        return jdbcClient.sql("""
                select p.id
                  from blog_posts p
                  join blog_post_likes recent on recent.post_id = p.id and recent.created_at >= :since
                 where""" + eligible.sql() + """
                 group by p.id, p.created_at
                 order by count(*) desc, p.created_at desc, p.id
                 limit :limit
                """).params(params).query((rs, rowNum) -> rs.getObject("id", UUID.class)).list();
    }

    /**
     * The newest eligible posts, at most {@code limit}, whenever they were written: what is left to show when nothing
     * fits and nothing was liked this week.
     */
    public List<UUID> latest(RecommendationFilter filter, int limit) {
        Eligible eligible = eligible(filter);
        Map<String, Object> params = new HashMap<>(eligible.params());
        params.put("limit", limit);
        return jdbcClient
                .sql("select p.id from blog_posts p where" + eligible.sql()
                        + " order by p.created_at desc, p.id limit :limit")
                .params(params).query((rs, rowNum) -> rs.getObject("id", UUID.class)).list();
    }

    /** The conditions every recommended post meets; the values - even the status - are bound. */
    private static Eligible eligible(RecommendationFilter filter) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        conditions.add("p.status = :published");
        params.put("published", BlogPostStatus.PUBLISHED.name());
        // "is distinct from", so that a post whose author is erased (null) is not left out as "not equal to".
        conditions.add("p.author_id is distinct from :viewer");
        params.put("viewer", filter.viewerId());
        conditions
                .add("not exists (select 1 from blog_post_saves sv where sv.post_id = p.id and sv.user_id = :viewer)");
        conditions
                .add("not exists (select 1 from blog_post_likes lk where lk.post_id = p.id and lk.user_id = :viewer)");
        if (!filter.hiddenAuthors().isEmpty()) {
            // Only added when there is something to hide - "not in ()" with no values is not valid SQL.
            conditions.add("(p.author_id is null or p.author_id not in (:hiddenAuthors))");
            params.put("hiddenAuthors", filter.hiddenAuthors());
        }
        return new Eligible(" " + String.join(" and ", conditions), params);
    }
}
