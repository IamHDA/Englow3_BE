package com.englow3.blog.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.englow3.blog.dto.projection.PostListFilter;
import com.englow3.blog.dto.projection.PostSummaryRow;
import com.englow3.blog.dto.projection.PostTagRow;
import com.englow3.blog.entity.BlogCommentStatus;
import com.englow3.blog.entity.BlogPostStatus;
import com.englow3.blog.entity.BlogTagKind;
import com.englow3.shared.persistence.SqlTime;

import lombok.RequiredArgsConstructor;

/**
 * Read model for the post list: posts with their like and comment counts and whether the viewer liked them, filtered by
 * tag, title and author, newest or most liked first. SQL rather than JPA because the filters combine freely and the
 * page must not cost a query per post.
 * <p>
 * The statement is put together from fixed fragments, one per filter that is present, and every value - the person, the
 * tags, the search text, the paging, even the status names - is bound as a parameter; nothing a user typed is ever part
 * of the SQL text. Reads only. The caller makes the excerpts and names the authors afterwards.
 */
@Component
@RequiredArgsConstructor
public class BlogPostListQuery {

    /** How much of the text a row carries: enough for an excerpt of 200 characters from text full of Markdown. */
    static final int BODY_HEAD_LENGTH = 1200;

    /** Marks the escaped characters in a LIKE pattern; the same character the flashcard catalogue search uses. */
    private static final char LIKE_ESCAPE = '!';

    private final JdbcClient jdbcClient;

    /** The page the filter names. Ties on the sort resolve by id so a page never repeats or skips a post. */
    public List<PostSummaryRow> list(PostListFilter filter) {
        Where where = where(filter);
        // MOST_LIKED counts the likes of every post that matches, on every call. Fine at this size; see the module map
        // for what to do when it is not. comment_count is every visible comment, whoever is asking: a small drift when
        // a block hides some of them, accepted rather than made a per-viewer count.
        String order = switch (filter.sort()) {
            case NEWEST -> "p.created_at desc, p.id";
            case MOST_LIKED -> "like_count desc, p.created_at desc, p.id";
        };
        Map<String, Object> params = new HashMap<>(where.params());
        params.put("viewer", filter.viewerId());
        params.put("bodyHeadLength", BODY_HEAD_LENGTH);
        params.put("commentVisible", BlogCommentStatus.VISIBLE.name());
        params.put("limit", filter.limit());
        params.put("offset", filter.offset());
        return jdbcClient.sql("""
                select p.id, p.author_id, p.title, p.status, left(p.body, :bodyHeadLength) as body_head,
                       p.created_at, p.edited_at,
                       (select count(*) from blog_post_likes l where l.post_id = p.id) as like_count,
                       (select count(*) from blog_comments c
                         where c.post_id = p.id and c.status = :commentVisible) as comment_count,
                       exists (select 1 from blog_post_likes l
                                where l.post_id = p.id and l.user_id = :viewer) as liked_by_me
                  from blog_posts p
                """ + where.sql() + " order by " + order + " limit :limit offset :offset").params(params)
                .query((rs, rowNum) -> new PostSummaryRow(rs.getObject("id", UUID.class),
                        rs.getObject("author_id", UUID.class), rs.getString("title"),
                        BlogPostStatus.valueOf(rs.getString("status")), rs.getString("body_head"),
                        SqlTime.read(rs, "created_at"), SqlTime.read(rs, "edited_at"), rs.getLong("like_count"),
                        rs.getLong("comment_count"), rs.getBoolean("liked_by_me")))
                .list();
    }

    /** How many posts the filter matches in all, paging aside, for the page's total. */
    public long count(PostListFilter filter) {
        Where where = where(filter);
        return jdbcClient.sql("select count(*) from blog_posts p" + where.sql()).params(where.params())
                .query(Long.class).single();
    }

    /** The tags of those posts, in one query. Nothing to ask for no posts. */
    public List<PostTagRow> tagsOf(Collection<UUID> postIds) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                select post_id, kind, tag_value
                  from blog_post_tags
                 where post_id in (:ids)
                 order by post_id, kind, tag_value
                """).param("ids", postIds).query((rs, rowNum) -> new PostTagRow(rs.getObject("post_id", UUID.class),
                BlogTagKind.valueOf(rs.getString("kind")), rs.getString("tag_value"))).list();
    }

    private record Where(String sql, Map<String, Object> params) {
    }

    /** The conditions the filter asks for, shared by the page and its total so the two always agree. */
    private static Where where(PostListFilter filter) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        if (!filter.includesEveryStatus()) {
            conditions.add("p.status = :published");
            params.put("published", BlogPostStatus.PUBLISHED.name());
        }
        if (!filter.hiddenAuthors().isEmpty()) {
            // A post whose author is erased (null) is nobody's to hide, and "null not in (...)" is not true: it is
            // named here or it would vanish from every list that has a block in it. Only added when there is
            // something to list - "not in ()" with no values is not valid SQL.
            conditions.add("(p.author_id is null or p.author_id not in (:hiddenAuthors))");
            params.put("hiddenAuthors", filter.hiddenAuthors());
        }
        if (filter.titleTerm() != null) {
            conditions.add("lower(p.title) like :titlePattern escape '" + LIKE_ESCAPE + "'");
            params.put("titlePattern", likePattern(filter.titleTerm()));
        }
        if (filter.authorId() != null) {
            conditions.add("p.author_id = :author");
            params.put("author", filter.authorId());
        }
        if (filter.skill() != null) {
            conditions.add(tagCondition("skillKind", "skill"));
            params.put("skillKind", BlogTagKind.SKILL.name());
            params.put("skill", filter.skill().name());
        }
        if (filter.level() != null) {
            conditions.add(tagCondition("levelKind", "level"));
            params.put("levelKind", BlogTagKind.LEVEL.name());
            params.put("level", filter.level().name());
        }
        if (filter.certificate() != null) {
            conditions.add(tagCondition("certificateKind", "certificate"));
            params.put("certificateKind", BlogTagKind.CERTIFICATE.name());
            params.put("certificate", filter.certificate().name());
        }
        String sql = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        return new Where(sql, params);
    }

    /** One tag the post must carry. The parameter names are fixed by the caller; the values are bound. */
    private static String tagCondition(String kindParam, String valueParam) {
        return "exists (select 1 from blog_post_tags t where t.post_id = p.id and t.kind = :" + kindParam
                + " and t.tag_value = :" + valueParam + ")";
    }

    /**
     * What was typed, lower case, with the characters LIKE treats as wild made plain: searching "100%" finds "100%".
     */
    private static String likePattern(String term) {
        String escaped = term.toLowerCase(Locale.ROOT).replace(LIKE_ESCAPE + "", LIKE_ESCAPE + "" + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%").replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped + "%";
    }
}
