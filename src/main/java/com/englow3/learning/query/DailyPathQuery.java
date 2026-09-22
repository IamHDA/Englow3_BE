package com.englow3.learning.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Read model for the daily path: what the learner has done, and what they still owe.
 * <p>
 * This is a declared cross-module reporting read. {@link #studyDays} and {@link #activityTotals} reach into
 * {@code exam_attempts}, which the exam module owns, because a streak that ignores exams would tell a learner who spent
 * two hours on a mock paper that they had not studied. Both are read-only and both are recorded in
 * {@code docs/module-map.md}; nothing here writes outside {@code learning}.
 */
@Component
@RequiredArgsConstructor
public class DailyPathQuery {

    private final JdbcClient jdbcClient;

    /**
     * Every day the learner did anything at all, newest first, across all four kinds of practice. Counted here rather
     * than per feature so that one screen cannot disagree with another about whether Tuesday counted.
     */
    public List<LocalDate> studyDays(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select distinct day from (
                  select cast(reviewed_at at time zone 'UTC' as date) as day
                    from flashcard_review_logs
                   where user_id = :userId and reviewed_at >= :from
                  union all
                  select cast(attempted_at at time zone 'UTC' as date)
                    from dictation_attempts
                   where user_id = :userId and attempted_at >= :from
                  union all
                  select cast(submitted_at at time zone 'UTC' as date)
                    from quiz_attempts
                   where user_id = :userId and submitted_at >= :from and status = 'SCORED'
                  union all
                  select cast(submitted_at at time zone 'UTC' as date)
                    from exam_attempts
                   where user_id = :userId and submitted_at >= :from and status = 'SCORED'
                ) activity
                 order by day desc
                """).param("userId", userId).param("from", from).query(LocalDate.class).list();
    }

    /**
     * Units of work done since {@code from}, which is what the experience counter is computed from. One round trip with
     * four subqueries rather than four calls: the page needs all four and they share nothing to join on.
     */
    public ActivityTotals activityTotals(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select
                  (select count(*) from flashcard_review_logs
                    where user_id = :userId and reviewed_at >= :from) as flashcard_reviews,
                  (select count(*) from dictation_attempts
                    where user_id = :userId and attempted_at >= :from) as dictation_sentences,
                  (select count(*) from quiz_attempts
                    where user_id = :userId and submitted_at >= :from and status = 'SCORED') as quiz_attempts,
                  (select count(*) from exam_attempts
                    where user_id = :userId and submitted_at >= :from and status = 'SCORED') as exam_attempts
                """).param("userId", userId).param("from", from)
                .query((rs, rowNum) -> new ActivityTotals(rs.getLong("flashcard_reviews"),
                        rs.getLong("dictation_sentences"), rs.getLong("quiz_attempts"), rs.getLong("exam_attempts")))
                .single();
    }

    public long cardsDue(UUID userId, Instant now) {
        return jdbcClient.sql("""
                select count(*) from flashcard_reviews
                 where user_id = :userId and due_at <= :now
                """).param("userId", userId).param("now", now).query(Long.class).single();
    }

    public long cardsReviewedSince(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select count(*) from flashcard_review_logs
                 where user_id = :userId and reviewed_at >= :from
                """).param("userId", userId).param("from", from).query(Long.class).single();
    }

    public long sentencesTypedSince(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select count(*) from dictation_attempts
                 where user_id = :userId and attempted_at >= :from
                """).param("userId", userId).param("from", from).query(Long.class).single();
    }

    /** Attempts that reached the quiz's own pass mark - the mark is per quiz, so it is compared per row. */
    public long quizzesPassedSince(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select count(*) from quiz_attempts
                 where user_id = :userId and submitted_at >= :from
                   and status = 'SCORED' and passed is true
                """).param("userId", userId).param("from", from).query(Long.class).single();
    }

    /**
     * Sets with cards the schedule has made due, most owed first. {@code completion_percent} is how much of the set the
     * learner has mastered, so a node can say how far in they are without a second query per row.
     */
    public List<DueSet> dueSets(UUID userId, Instant now, int limit) {
        return jdbcClient.sql("""
                select s.id, s.name,
                       count(*) filter (where r.due_at <= :now) as due_count,
                       cast(round(
                         100.0 * count(*) filter (where r.status = 'MASTERED') / nullif(count(*), 0)
                       ) as integer) as completion_percent
                  from flashcard_reviews r
                  join flashcards c on c.id = r.flashcard_id
                  join flashcard_sets s on s.id = c.flashcard_set_id
                 where r.user_id = :userId and s.status = 'PUBLISHED'
                 group by s.id, s.name
                having count(*) filter (where r.due_at <= :now) > 0
                 order by due_count desc, s.name
                 limit :limit
                """).param("userId", userId).param("now", now).param("limit", limit)
                .query((rs, rowNum) -> new DueSet(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getLong("due_count"), rs.getInt("completion_percent")))
                .list();
    }

    /** Sets the learner rated cards in since {@code from}, with how well those answers went. */
    public List<StudiedSet> setsStudiedSince(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select s.id, s.name, count(*) as card_count,
                       cast(round(
                         100.0 * count(*) filter (where l.rating <> 'AGAIN') / nullif(count(*), 0)
                       ) as integer) as recall_percent
                  from flashcard_review_logs l
                  join flashcard_sets s on s.id = l.flashcard_set_id
                 where l.user_id = :userId and l.reviewed_at >= :from
                 group by s.id, s.name
                 order by card_count desc
                """).param("userId", userId).param("from", from)
                .query((rs, rowNum) -> new StudiedSet(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getLong("card_count"), rs.getInt("recall_percent")))
                .list();
    }

    /**
     * Published quizzes the learner has not passed, including ones they have never opened. Ordered so a quiz they came
     * close on comes before one they have never seen: finishing something started beats starting something new.
     */
    public List<PendingQuiz> unpassedQuizzes(UUID userId, int limit) {
        return jdbcClient.sql("""
                select q.id, q.title,
                       (select count(*) from quiz_questions where quiz_id = q.id) as question_count,
                       best.score_percentage as best_score
                  from quizzes q
                  left join lateral (
                    select max(a.score_percentage) as score_percentage
                      from quiz_attempts a
                     where a.quiz_id = q.id and a.user_id = :userId and a.status = 'SCORED'
                  ) best on true
                 where q.status = 'PUBLISHED'
                   and coalesce(best.score_percentage, -1) < q.passing_score_percent
                 order by best.score_percentage desc nulls last, q.title
                 limit :limit
                """).param("userId", userId).param("limit", limit).query((rs, rowNum) -> {
            BigDecimal best = rs.getObject("best_score", BigDecimal.class);
            return new PendingQuiz(rs.getObject("id", UUID.class), rs.getString("title"), rs.getLong("question_count"),
                    best == null ? null : best.intValue());
        }).list();
    }

    /** Quizzes submitted since {@code from}, newest first, with the score that submission earned. */
    public List<AttemptedQuiz> quizzesAttemptedSince(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select q.id, q.title, a.score_percentage
                  from quiz_attempts a
                  join quizzes q on q.id = a.quiz_id
                 where a.user_id = :userId and a.submitted_at >= :from and a.status = 'SCORED'
                 order by a.submitted_at desc
                """).param("userId", userId).param("from", from).query((rs, rowNum) -> {
            BigDecimal score = rs.getObject("score_percentage", BigDecimal.class);
            return new AttemptedQuiz(rs.getObject("id", UUID.class), rs.getString("title"),
                    score == null ? null : score.intValue());
        }).list();
    }

    /**
     * Published lessons with sentences the learner has not cleared. The threshold is passed in so this agrees with the
     * service that decides what "cleared" means rather than keeping a second copy of the number.
     */
    public List<PendingLesson> unfinishedLessons(UUID userId, BigDecimal threshold, int limit) {
        return jdbcClient.sql("""
                select lesson.id, lesson.title, lesson.total - lesson.cleared as remaining,
                       cast(round(100.0 * lesson.cleared / nullif(lesson.total, 0)) as integer) as completion_percent,
                       lesson.attempted
                  from (
                    select l.id, l.title,
                           count(*) as total,
                           count(*) filter (where best.accuracy >= :threshold) as cleared,
                           count(*) filter (where best.accuracy is not null) as attempted
                      from dictation_lessons l
                      join dictation_sentences s on s.dictation_lesson_id = l.id
                      left join lateral (
                        select max(a.accuracy_percent) as accuracy
                          from dictation_attempts a
                         where a.dictation_sentence_id = s.id and a.user_id = :userId
                      ) best on true
                     where l.status = 'PUBLISHED'
                     group by l.id, l.title
                  ) lesson
                 where lesson.total > lesson.cleared
                 order by lesson.attempted desc, lesson.title
                 limit :limit
                """).param("userId", userId).param("threshold", threshold).param("limit", limit)
                .query((rs, rowNum) -> new PendingLesson(rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getLong("remaining"), rs.getInt("completion_percent")))
                .list();
    }

    /** Lessons the learner transcribed in since {@code from}, with how accurate those attempts were. */
    public List<PractisedLesson> lessonsPractisedSince(UUID userId, Instant from) {
        return jdbcClient.sql("""
                select l.id, l.title, count(*) as sentence_count,
                       cast(round(avg(a.accuracy_percent)) as integer) as accuracy_percent
                  from dictation_attempts a
                  join dictation_lessons l on l.id = a.dictation_lesson_id
                 where a.user_id = :userId and a.attempted_at >= :from
                 group by l.id, l.title
                 order by sentence_count desc
                """).param("userId", userId).param("from", from)
                .query((rs, rowNum) -> new PractisedLesson(rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getLong("sentence_count"), rs.getInt("accuracy_percent")))
                .list();
    }

    public record ActivityTotals(long flashcardReviews, long dictationSentences, long quizAttempts, long examAttempts) {
    }

    public record DueSet(UUID setId, String name, long dueCount, int completionPercent) {
    }

    public record StudiedSet(UUID setId, String name, long cardCount, int recallPercent) {
    }

    public record PendingQuiz(UUID quizId, String title, long questionCount, Integer bestScorePercent) {
    }

    public record AttemptedQuiz(UUID quizId, String title, Integer scorePercent) {
    }

    public record PendingLesson(UUID lessonId, String title, long remainingSentences, int completionPercent) {
    }

    public record PractisedLesson(UUID lessonId, String title, long sentenceCount, int accuracyPercent) {
    }
}
