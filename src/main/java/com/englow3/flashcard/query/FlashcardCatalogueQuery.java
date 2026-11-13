package com.englow3.flashcard.query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.englow3.flashcard.dto.result.FlashcardSetSummaryResult;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.persistence.SqlTime;

@Component
public class FlashcardCatalogueQuery {
    private final JdbcClient jdbc;

    public FlashcardCatalogueQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

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
               where s.status = 'PUBLISHED'
            )
            """;
    private static final String FILTER = """
            from catalogue where (:topic = '' or lower(topic) = lower(:topic))
              and (:term = '' or lower(name) like :pattern escape '!'
                   or lower(description) like :pattern escape '!')
            """;

    public Page<FlashcardSetSummaryResult> search(UUID userId, Instant now, String topic, String term, String sort,
            Pageable page) {
        String order = switch (sort) {
            case "due" -> "due_count desc";
            case "progress" -> "(mastered_count::numeric / nullif(card_count, 0)) desc nulls last";
            case "cards" -> "card_count desc";
            default -> throw new BadRequestException("FLASHCARD_SORT_INVALID", "Unknown flashcard sort");
        };
        String query = term == null ? "" : term.trim().toLowerCase(java.util.Locale.ROOT);
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource().addValue("userId", userId)
                .addValue("now", SqlTime.at(now)).addValue("topic", topic == null ? "" : topic.trim())
                .addValue("term", query).addValue("pattern", pattern).addValue("limit", page.getPageSize())
                .addValue("offset", page.getOffset());
        long total = jdbc.sql(CATALOGUE + "select count(*) " + FILTER).paramSource(params).query(Long.class).single();
        var items = jdbc
                .sql(CATALOGUE
                        + "select * " + FILTER + " order by " + order + ", lower(name), id limit :limit offset :offset")
                .paramSource(params)
                .query((rs, row) -> new FlashcardSetSummaryResult(rs.getObject("id", UUID.class), rs.getString("slug"),
                        rs.getString("name"), rs.getString("description"), rs.getString("topic"),
                        rs.getString("target_level"), rs.getLong("card_count"), rs.getLong("due_count"),
                        rs.getLong("mastered_count"), rs.getTimestamp("last_studied_at") == null ? null
                                : rs.getTimestamp("last_studied_at").toInstant()))
                .list();
        return new PageImpl<>(items, page, total);
    }

    public com.englow3.flashcard.dto.result.FlashcardCatalogueInfoResult information(UUID userId, Instant now) {
        List<String> topics = jdbc
                .sql("select distinct topic from flashcard_sets where status = 'PUBLISHED' order by topic")
                .query(String.class).list();
        long due = jdbc.sql("""
                select count(*) from flashcard_reviews r join flashcards c on c.id = r.flashcard_id
                join flashcard_sets s on s.id = c.flashcard_set_id
                where r.user_id = :userId and r.due_at <= :now and s.status = 'PUBLISHED'
                """).param("userId", userId).param("now", SqlTime.at(now)).query(Long.class).single();
        return new com.englow3.flashcard.dto.result.FlashcardCatalogueInfoResult(topics, due);
    }
}
