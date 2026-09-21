package com.englow3.learning.query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Read model for the flashcard statistics screen. SQL rather than JPA, and its own class rather than a repository
 * method: every one of these is a grouped aggregate that returns a projection no entity matches. Expressing them
 * through the ORM would mean either loading every log row into memory to count it, or writing the same SQL inside an
 * {@code @Query} where it is harder to read.
 */
@Component
@RequiredArgsConstructor
public class FlashcardStatsQuery {

    private final JdbcClient jdbcClient;

    /** How many distinct cards the learner has answered at least once. */
    public long cardsStudied(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select count(distinct flashcard_id) from flashcard_review_logs
                 where user_id = :userId and reviewed_at >= :from
                """).param("userId", userId).param("from", from).query(Long.class).single();
    }

    /**
     * Share of answers that were not failures. "Retention" in the interface sense: how often a card the learner met
     * came back to them, counted over answers rather than over cards, so a word they keep failing weighs as often as
     * they meet it.
     */
    public int retentionPercent(UUID userId, Instant from) {
        Integer percent = jdbcClient.sql("""
                select cast(round(
                         100.0 * count(*) filter (where rating <> 'AGAIN') / nullif(count(*), 0)
                       ) as integer)
                  from flashcard_review_logs
                 where user_id = :userId and reviewed_at >= :from
                """).param("userId", userId).param("from", from).query(Integer.class).optional().orElse(0);
        return percent == null ? 0 : percent;
    }

    public long studySeconds(UUID userId, Instant from) {
        Long seconds = jdbcClient.sql("""
                select coalesce(sum(time_spent_seconds), 0) from flashcard_review_logs
                 where user_id = :userId and reviewed_at >= :from
                """).param("userId", userId).param("from", from).query(Long.class).single();
        return seconds == null ? 0 : seconds;
    }

    /** One row per day the learner answered anything, newest last so a chart can plot it straight. */
    public List<DailyActivity> activityByDay(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select cast(reviewed_at at time zone 'UTC' as date) as day, count(*) as cards
                  from flashcard_review_logs
                 where user_id = :userId and reviewed_at >= :from
                 group by day
                 order by day
                """).param("userId", userId).param("from", from)
                .query((rs, rowNum) -> new DailyActivity(rs.getObject("day", LocalDate.class), rs.getLong("cards")))
                .list();
    }

    /**
     * The cards that keep catching this learner out, worst first. Ordered by lapses rather than by failures: a lapse is
     * a card they had learned and then lost, which is what "difficult" means here. A card failed three times while
     * still being learned is just new.
     */
    public List<DifficultCard> difficultCards(UUID userId, int limit) {
        return jdbcClient.sql("""
                select c.id, c.lemma, s.name as set_name, r.lapse_count, r.last_reviewed_at
                  from flashcard_reviews r
                  join flashcards c on c.id = r.flashcard_id
                  join flashcard_sets s on s.id = c.flashcard_set_id
                 where r.user_id = :userId and r.lapse_count > 0
                 order by r.lapse_count desc, r.last_reviewed_at desc
                 limit :limit
                """).param("userId", userId).param("limit", limit)
                .query((rs, rowNum) -> new DifficultCard(rs.getObject("id", UUID.class), rs.getString("lemma"),
                        rs.getString("set_name"), rs.getInt("lapse_count"),
                        rs.getObject("last_reviewed_at", Instant.class)))
                .list();
    }

    /** One row per day and set - the history table reads as "on this day, in this set, you did this". */
    public List<SessionSummary> history(UUID userId, int limit) {
        return jdbcClient.sql("""
                select cast(l.reviewed_at at time zone 'UTC' as date) as day,
                       s.id as set_id,
                       s.name as set_name,
                       count(*) as cards,
                       cast(round(
                         100.0 * count(*) filter (where l.rating <> 'AGAIN') / nullif(count(*), 0)
                       ) as integer) as recall_percent,
                       coalesce(sum(l.time_spent_seconds), 0) as study_seconds
                  from flashcard_review_logs l
                  join flashcard_sets s on s.id = l.flashcard_set_id
                 where l.user_id = :userId
                 group by day, s.id, s.name
                 order by day desc
                 limit :limit
                """).param("userId", userId).param("limit", limit)
                .query((rs, rowNum) -> new SessionSummary(rs.getObject("day", LocalDate.class),
                        rs.getObject("set_id", UUID.class), rs.getString("set_name"), rs.getLong("cards"),
                        rs.getInt("recall_percent"), rs.getLong("study_seconds")))
                .list();
    }

    /**
     * Every day the learner answered a card, newest first. The streak is counted from this in the service rather than
     * in SQL: window functions over dates are where this kind of query stops being readable, and the list is small.
     */
    public List<LocalDate> studyDays(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select distinct cast(reviewed_at at time zone 'UTC' as date) as day
                  from flashcard_review_logs
                 where user_id = :userId and reviewed_at >= :from
                 order by day desc
                """).param("userId", userId).param("from", from).query(LocalDate.class).list();
    }

    public record DailyActivity(LocalDate day, long cardCount) {
    }

    public record DifficultCard(UUID flashcardId, String lemma, String setName, int lapseCount, Instant lastReviewed) {
    }

    public record SessionSummary(LocalDate day, UUID setId, String setName, long cardCount, int recallPercent,
            long studySeconds) {
    }
}
