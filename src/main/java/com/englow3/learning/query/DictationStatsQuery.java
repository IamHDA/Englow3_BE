package com.englow3.learning.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.englow3.shared.persistence.SqlTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Read model for the dictation statistics screen. Same reasoning as {@link FlashcardStatsQuery}. */
@Component
@RequiredArgsConstructor
public class DictationStatsQuery {

    private final JdbcClient jdbcClient;

    /**
     * Lessons where every sentence has been cleared. The threshold is applied here as well as in the service that shows
     * progress, so the two agree on what "completed" means.
     */
    public long lessonsCompleted(UUID userId, BigDecimal threshold) {
        Long count = jdbcClient.sql("""
                select count(*) from (
                  select s.dictation_lesson_id,
                         count(*) as total,
                         count(*) filter (where best.accuracy >= :threshold) as cleared
                    from dictation_sentences s
                    left join lateral (
                      select max(a.accuracy_percent) as accuracy
                        from dictation_attempts a
                       where a.dictation_sentence_id = s.id and a.user_id = :userId
                    ) best on true
                   group by s.dictation_lesson_id
                ) lesson
                 where lesson.total > 0 and lesson.total = lesson.cleared
                """).param("userId", userId).param("threshold", threshold).query(Long.class).single();
        return count == null ? 0 : count;
    }

    public int averageAccuracy(UUID userId, Instant from) {
        Integer average = jdbcClient.sql("""
                select cast(round(coalesce(avg(accuracy_percent), 0)) as integer)
                  from dictation_attempts
                 where user_id = :userId and attempted_at >= :from
                """).param("userId", userId).param("from", SqlTime.at(from)).query(Integer.class).optional().orElse(0);
        return average == null ? 0 : average;
    }

    /**
     * Seconds of audio the learner sat through. Counted per attempt rather than per distinct sentence: replaying a line
     * to type it again is listening practice, and not counting it would under-report the work.
     */
    public long listeningSeconds(UUID userId, Instant from) {
        Long seconds = jdbcClient.sql("""
                select coalesce(sum(s.audio_duration_seconds), 0)
                  from dictation_attempts a
                  join dictation_sentences s on s.id = a.dictation_sentence_id
                 where a.user_id = :userId and a.attempted_at >= :from
                """).param("userId", userId).param("from", SqlTime.at(from)).query(Long.class).single();
        return seconds == null ? 0 : seconds;
    }

    public long sentencesPractised(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select count(distinct dictation_sentence_id) from dictation_attempts
                 where user_id = :userId and attempted_at >= :from
                """).param("userId", userId).param("from", SqlTime.at(from)).query(Long.class).single();
    }

    public List<DailyAccuracy> accuracyByDay(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select cast(attempted_at at time zone 'UTC' as date) as day,
                       cast(round(avg(accuracy_percent)) as integer) as accuracy,
                       count(*) as attempts
                  from dictation_attempts
                 where user_id = :userId and attempted_at >= :from
                 group by day
                 order by day
                """).param("userId", userId).param("from", SqlTime.at(from))
                .query((rs, rowNum) -> new DailyAccuracy(rs.getObject("day", LocalDate.class), rs.getInt("accuracy"),
                        rs.getLong("attempts")))
                .list();
    }

    /** Lines the learner scores worst on, over lines they have tried more than once. */
    public List<DifficultSentence> difficultSentences(UUID userId, int limit) {
        return jdbcClient.sql("""
                select s.id, s.text, l.topic,
                       cast(round(avg(a.accuracy_percent)) as integer) as accuracy,
                       count(*) as attempts
                  from dictation_attempts a
                  join dictation_sentences s on s.id = a.dictation_sentence_id
                  join dictation_lessons l on l.id = s.dictation_lesson_id
                 where a.user_id = :userId
                 group by s.id, s.text, l.topic
                having count(*) > 1
                 order by avg(a.accuracy_percent) asc
                 limit :limit
                """).param("userId", userId).param("limit", limit)
                .query((rs, rowNum) -> new DifficultSentence(rs.getObject("id", UUID.class), rs.getString("text"),
                        rs.getString("topic"), rs.getInt("accuracy"), rs.getLong("attempts")))
                .list();
    }

    public List<SessionSummary> history(UUID userId, int limit) {
        return jdbcClient.sql("""
                select cast(a.attempted_at at time zone 'UTC' as date) as day,
                       l.id as lesson_id,
                       l.title as lesson_title,
                       count(*) as sentences,
                       cast(round(avg(a.accuracy_percent)) as integer) as accuracy,
                       coalesce(sum(s.audio_duration_seconds), 0) as listening_seconds
                  from dictation_attempts a
                  join dictation_sentences s on s.id = a.dictation_sentence_id
                  join dictation_lessons l on l.id = a.dictation_lesson_id
                 where a.user_id = :userId
                 group by day, l.id, l.title
                 order by day desc
                 limit :limit
                """).param("userId", userId).param("limit", limit)
                .query((rs, rowNum) -> new SessionSummary(rs.getObject("day", LocalDate.class),
                        rs.getObject("lesson_id", UUID.class), rs.getString("lesson_title"), rs.getLong("sentences"),
                        rs.getInt("accuracy"), rs.getLong("listening_seconds")))
                .list();
    }

    /**
     * Recent answers paired with what they should have been, for the missed-word count. The words are compared in Java
     * rather than in SQL because the comparison rules - case, punctuation, position - already live in
     * {@code DictationScorer}, and a second implementation in SQL is how the two start disagreeing about what a word
     * is.
     */
    public List<AttemptText> recentAttemptTexts(UUID userId, Instant from, int limit) {
        return jdbcClient.sql("""
                select s.text as expected, a.response as actual, s.id as sentence_id
                  from dictation_attempts a
                  join dictation_sentences s on s.id = a.dictation_sentence_id
                 where a.user_id = :userId and a.attempted_at >= :from
                 order by a.attempted_at desc
                 limit :limit
                """).param("userId", userId).param("from", SqlTime.at(from)).param("limit", limit)
                .query((rs, rowNum) -> new AttemptText(rs.getObject("sentence_id", UUID.class),
                        rs.getString("expected"), rs.getString("actual")))
                .list();
    }

    public List<LocalDate> practiceDays(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select distinct cast(attempted_at at time zone 'UTC' as date) as day
                  from dictation_attempts
                 where user_id = :userId and attempted_at >= :from
                 order by day desc
                """).param("userId", userId).param("from", SqlTime.at(from)).query(LocalDate.class).list();
    }

    /**
     * The lines this learner keeps getting wrong, with everything needed to practise them again: the clip, how long it
     * runs, and the last thing they typed.
     * <p>
     * Ordered by best accuracy rather than by average. A line whose best attempt is still 60% is one they have never
     * got right; averaging in early failures would push a sentence they have since mastered back to the top.
     * <p>
     * The correct text is here because this screen shows the answer after the learner commits one, exactly as a normal
     * attempt does - it is the review screen for lines already answered, not a way to read the key in advance.
     */
    public List<MistakeSentence> mistakeQueue(UUID userId, BigDecimal threshold, int limit) {
        return jdbcClient.sql("""
                select s.id, s.text, s.audio_object_key, s.audio_duration_seconds,
                       l.id as lesson_id, l.title as lesson_title,
                       cast(round(max(a.accuracy_percent)) as integer) as best_accuracy,
                       count(*) as attempts,
                       (array_agg(a.response order by a.attempted_at desc))[1] as last_response
                  from dictation_attempts a
                  join dictation_sentences s on s.id = a.dictation_sentence_id
                  join dictation_lessons l on l.id = s.dictation_lesson_id
                 where a.user_id = :userId
                 group by s.id, s.text, s.audio_object_key, s.audio_duration_seconds, l.id, l.title
                having max(a.accuracy_percent) < :threshold
                 order by max(a.accuracy_percent) asc, count(*) desc
                 limit :limit
                """).param("userId", userId).param("threshold", threshold).param("limit", limit)
                .query((rs, rowNum) -> new MistakeSentence(rs.getObject("id", UUID.class), rs.getString("text"),
                        rs.getString("audio_object_key"), rs.getInt("audio_duration_seconds"),
                        rs.getObject("lesson_id", UUID.class), rs.getString("lesson_title"), rs.getInt("best_accuracy"),
                        rs.getLong("attempts"), rs.getString("last_response")))
                .list();
    }

    public record MistakeSentence(UUID sentenceId, String text, String audioObjectKey, int audioDurationSeconds,
            UUID lessonId, String lessonTitle, int bestAccuracyPercent, long attemptCount, String lastResponse) {
    }

    public record DailyAccuracy(LocalDate day, int accuracyPercent, long attemptCount) {
    }

    public record DifficultSentence(UUID sentenceId, String text, String topic, int accuracyPercent,
            long attemptCount) {
    }

    public record SessionSummary(LocalDate day, UUID lessonId, String lessonTitle, long sentenceCount,
            int accuracyPercent, long listeningSeconds) {
    }

    public record AttemptText(UUID sentenceId, String expected, String actual) {
    }
}
