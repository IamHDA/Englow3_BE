package com.englow3.blog.query;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.englow3.blog.dto.projection.QueueRow;
import com.englow3.blog.dto.projection.TargetPreviewRow;
import com.englow3.blog.entity.BlogQueueSort;
import com.englow3.blog.entity.BlogReportReason;
import com.englow3.blog.entity.BlogReportStatus;
import com.englow3.blog.entity.BlogTargetType;
import com.englow3.shared.persistence.SqlTime;

import lombok.RequiredArgsConstructor;

/**
 * Read model for the moderation queue: the content that has open reports, one row for each piece of content however
 * many readers reported it, with how many, why, and when. SQL rather than JPA because it groups, and because the page
 * must not cost a query per row: the start of each post and comment is read afterwards, one query for the posts and one
 * for the comments of the whole page.
 * <p>
 * The statement is put together from fixed fragments and every value - the status, the type, the paging - is bound as a
 * parameter; the order is chosen from the two orders there are and never typed. Reads only.
 */
@Component
@RequiredArgsConstructor
public class BlogModerationQueueQuery {

    /** How much of the text a preview carries: enough for an excerpt of 200 characters from text full of Markdown. */
    static final int BODY_HEAD_LENGTH = 400;

    private final JdbcClient jdbcClient;

    /**
     * The page of content with open reports, oldest first or most reported first. Ties resolve by the content's id so a
     * page never repeats or skips a row.
     */
    public List<QueueRow> queue(BlogTargetType type, BlogQueueSort sort, int limit, int offset) {
        String order = switch (sort) {
            case OLDEST -> "min(r.created_at), r.target_id";
            case MOST_REPORTED -> "count(*) desc, min(r.created_at), r.target_id";
        };
        Map<String, Object> params = new HashMap<>();
        params.put("open", BlogReportStatus.OPEN.name());
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbcClient
                .sql("""
                        select r.target_type, r.target_id,
                               count(*) as open_reports,
                               array_agg(distinct r.reason) as reasons,
                               min(r.created_at) as first_reported_at,
                               max(r.created_at) as last_reported_at
                          from blog_reports r
                         where r.status = :open""" + typeCondition(type, params)
                        + " group by r.target_type, r.target_id order by " + order + " limit :limit offset :offset")
                .params(params)
                .query((rs, rowNum) -> new QueueRow(BlogTargetType.valueOf(rs.getString("target_type")),
                        rs.getObject("target_id", UUID.class), rs.getLong("open_reports"),
                        reasons(rs.getArray("reasons")), SqlTime.read(rs, "first_reported_at"),
                        SqlTime.read(rs, "last_reported_at")))
                .list();
    }

    /** How many pieces of content have open reports, paging aside, for the page's total. */
    public long count(BlogTargetType type) {
        Map<String, Object> params = new HashMap<>();
        params.put("open", BlogReportStatus.OPEN.name());
        return jdbcClient
                .sql("select count(*) from (select 1 from blog_reports r where r.status = :open"
                        + typeCondition(type, params) + " group by r.target_type, r.target_id) open_groups")
                .params(params).query(Long.class).single();
    }

    /**
     * The start of each of these posts and comments, one query for the posts and one for the comments, and none for a
     * kind with no ids. Content that is no longer there - its author deleted it - is simply not in the answer.
     */
    public List<TargetPreviewRow> previews(Collection<UUID> postIds, Collection<UUID> commentIds) {
        List<TargetPreviewRow> rows = new ArrayList<>();
        if (!postIds.isEmpty()) {
            rows.addAll(jdbcClient.sql("""
                    select id, author_id, title, left(body, :bodyHeadLength) as body_head, status, created_at
                      from blog_posts
                     where id in (:ids)
                    """).param("bodyHeadLength", BODY_HEAD_LENGTH).param("ids", postIds)
                    .query((rs, rowNum) -> new TargetPreviewRow(BlogTargetType.POST, rs.getObject("id", UUID.class),
                            rs.getObject("author_id", UUID.class), rs.getString("title"), rs.getString("body_head"),
                            rs.getString("status"), SqlTime.read(rs, "created_at"), null))
                    .list());
        }
        if (!commentIds.isEmpty()) {
            rows.addAll(jdbcClient.sql("""
                    select c.id, c.author_id, left(c.body, :bodyHeadLength) as body_head, c.status, c.created_at,
                           c.post_id, p.title as post_title
                      from blog_comments c
                      join blog_posts p on p.id = c.post_id
                     where c.id in (:ids)
                    """).param("bodyHeadLength", BODY_HEAD_LENGTH).param("ids", commentIds)
                    .query((rs, rowNum) -> new TargetPreviewRow(BlogTargetType.COMMENT, rs.getObject("id", UUID.class),
                            rs.getObject("author_id", UUID.class), rs.getString("post_title"),
                            rs.getString("body_head"), rs.getString("status"), SqlTime.read(rs, "created_at"),
                            rs.getObject("post_id", UUID.class)))
                    .list());
        }
        return rows;
    }

    /** The condition for one kind of content when a kind is asked for, with its value bound; nothing otherwise. */
    private static String typeCondition(BlogTargetType type, Map<String, Object> params) {
        if (type == null) {
            return "";
        }
        params.put("type", type.name());
        return " and r.target_type = :type";
    }

    private static List<BlogReportReason> reasons(Array array) throws SQLException {
        String[] names = (String[]) array.getArray();
        List<BlogReportReason> reasons = new ArrayList<>(names.length);
        for (String name : names) {
            reasons.add(BlogReportReason.valueOf(name));
        }
        return reasons;
    }
}
