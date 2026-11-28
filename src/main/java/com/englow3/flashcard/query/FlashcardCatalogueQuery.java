package com.englow3.flashcard.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.englow3.flashcard.dto.command.FlashcardSetScope;
import com.englow3.flashcard.dto.result.FlashcardCatalogueInfoResult;
import com.englow3.flashcard.dto.result.FlashcardSetSummaryResult;
import com.englow3.flashcard.entity.FlashcardSetKind;
import com.englow3.flashcard.entity.FlashcardSetVisibility;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.persistence.SqlTime;

@Component
public class FlashcardCatalogueQuery {
    private final JdbcClient jdbc;

    public FlashcardCatalogueQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Never a user id. Stands in for an empty friend or block list, because {@code in ()} is not valid SQL and an empty
     * list must still mean "nobody".
     */
    private static final UUID NOBODY = new UUID(0L, 0L);

    /**
     * Which sets {@code :userId} may open - the SQL form of {@code FlashcardSet.isVisibleTo}, for lists, where loading
     * every set to ask it would be absurd. Keep the two in step. {@code :friends} and {@code :blocked} come from the
     * friendship module's reader; this module never reads its tables.
     */
    private static final String VISIBLE = """
            (s.status = 'PUBLISHED' and (
               s.kind = 'CATALOGUE'
               or s.created_by_user_id = :userId
               or (s.created_by_user_id not in (:blocked) and (
                     s.visibility = 'PUBLIC'
                     or (s.visibility = 'SELECTED_FRIENDS' and s.created_by_user_id in (:friends)
                         and exists (select 1 from flashcard_set_shares sh
                                      where sh.flashcard_set_id = s.id and sh.user_id = :userId))))))
            """;

    private static final String CATALOGUE = """
            with catalogue as (
              select s.*, coalesce(c.card_count, 0) as card_count,
                     coalesce(r.due_count, 0) as due_count, coalesce(r.mastered_count, 0) as mastered_count,
                     l.last_studied_at
                from flashcard_sets s
                left join (select flashcard_set_id, count(*) as card_count from flashcards
                           group by flashcard_set_id) c on c.flashcard_set_id = s.id
                left join (select c.flashcard_set_id,
                                  count(*) filter (where r.due_at <= :now) as due_count,
                                  count(*) filter (where r.status = 'MASTERED') as mastered_count
                             from flashcard_reviews r join flashcards c on c.id = r.flashcard_id
                            where r.user_id = :userId group by c.flashcard_set_id) r on r.flashcard_set_id = s.id
                left join (select flashcard_set_id, max(reviewed_at) as last_studied_at
                             from flashcard_review_logs where user_id = :userId
                            group by flashcard_set_id) l on l.flashcard_set_id = s.id
               where %s
            )
            """;
    private static final String FILTER = """
            from catalogue where (:topic = '' or lower(topic) = lower(:topic))
              and (:term = '' or lower(name) like :pattern escape '!'
                   or lower(description) like :pattern escape '!')
            """;

    public Page<FlashcardSetSummaryResult> search(UUID userId, Collection<UUID> friendIds, Collection<UUID> blockedIds,
            Instant now, FlashcardSetScope scope, String topic, String term, String sort, Pageable page) {
        String order = switch (sort) {
            case "due" -> "due_count desc";
            case "progress" -> "(mastered_count::numeric / nullif(card_count, 0)) desc nulls last";
            case "cards" -> "card_count desc";
            case "recent" -> "created_at desc";
            default -> throw new BadRequestException("FLASHCARD_SORT_INVALID", "Unknown flashcard sort");
        };
        String shelf = switch (scope) {
            case CATALOGUE -> "s.kind = 'CATALOGUE' and s.status = 'PUBLISHED'";
            case MINE -> "s.kind = 'PERSONAL' and s.created_by_user_id = :userId";
            case COMMUNITY -> "s.kind = 'PERSONAL' and s.created_by_user_id <> :userId and " + VISIBLE;
        };
        String query = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var params = audience(userId, friendIds, blockedIds).addValue("now", SqlTime.at(now))
                .addValue("topic", topic == null ? "" : topic.trim()).addValue("term", query)
                .addValue("pattern", pattern).addValue("limit", page.getPageSize())
                .addValue("offset", page.getOffset());
        String with = CATALOGUE.formatted(shelf);
        long total = jdbc.sql(with + "select count(*) " + FILTER).paramSource(params).query(Long.class).single();
        var items = jdbc.sql(
                with + "select * " + FILTER + " order by " + order + ", lower(name), id limit :limit offset :offset")
                .paramSource(params).query((rs, row) -> {
                    FlashcardSetKind kind = FlashcardSetKind.valueOf(rs.getString("kind"));
                    return new FlashcardSetSummaryResult(rs.getObject("id", UUID.class), rs.getString("slug"),
                            rs.getString("name"), rs.getString("description"), rs.getString("topic"),
                            rs.getString("target_level"), rs.getLong("card_count"), rs.getLong("due_count"),
                            rs.getLong("mastered_count"),
                            rs.getTimestamp("last_studied_at") == null ? null
                                    : rs.getTimestamp("last_studied_at").toInstant(),
                            kind, FlashcardSetVisibility.valueOf(rs.getString("visibility")),
                            kind == FlashcardSetKind.PERSONAL ? rs.getObject("created_by_user_id", UUID.class) : null,
                            null, kind == FlashcardSetKind.PERSONAL
                                    && userId.equals(rs.getObject("created_by_user_id", UUID.class)));
                }).list();
        return new PageImpl<>(items, page, total);
    }

    /**
     * Topics are the catalogue's vocabulary, so a learner's own set does not add one. The due count covers every set
     * the learner can still open, their own and shared ones included - a card due in a set they have lost access to is
     * not something they can act on.
     */
    public FlashcardCatalogueInfoResult information(UUID userId, Collection<UUID> friendIds,
            Collection<UUID> blockedIds, Instant now) {
        List<String> topics = jdbc.sql(
                "select distinct topic from flashcard_sets where status = 'PUBLISHED' and kind = 'CATALOGUE' order by topic")
                .query(String.class).list();
        long due = jdbc.sql("""
                select count(*) from flashcard_reviews r join flashcards c on c.id = r.flashcard_id
                join flashcard_sets s on s.id = c.flashcard_set_id
                where r.user_id = :userId and r.due_at <= :now and
                """ + VISIBLE).paramSource(audience(userId, friendIds, blockedIds).addValue("now", SqlTime.at(now)))
                .query(Long.class).single();
        return new FlashcardCatalogueInfoResult(topics, due);
    }

    private static MapSqlParameterSource audience(UUID userId, Collection<UUID> friendIds,
            Collection<UUID> blockedIds) {
        return new MapSqlParameterSource().addValue("userId", userId).addValue("friends", orNobody(friendIds))
                .addValue("blocked", orNobody(blockedIds));
    }

    private static List<UUID> orNobody(Collection<UUID> ids) {
        List<UUID> list = new ArrayList<>(ids);
        list.add(NOBODY);
        return list;
    }
}
