package com.englow3.learning.query;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.englow3.shared.persistence.SqlTime;

import lombok.RequiredArgsConstructor;

/**
 * The figures on the administrator's overview: what is waiting on review, how much is live, and whether anyone is
 * using it.
 * <p>
 * A declared cross-module read (see {@code docs/module-map.md}): it counts {@code speaking_prompts}, {@code exams},
 * {@code exam_attempts} and {@code users} alongside this module's own tables. Read-only, and in two round trips rather
 * than one per figure - the page asks for about twenty numbers.
 */
@Component
@RequiredArgsConstructor
public class AdminOverviewQuery {

    private final JdbcClient jdbcClient;

    /** One row per kind of content, in the order the overview lists them. */
    public List<ContentCounts> contentCounts() {
        return jdbcClient.sql("""
                select 'FLASHCARD_SET' as kind,
                       count(*) filter (where status = 'DRAFT') as drafts,
                       count(*) filter (where status = 'PENDING_REVIEW') as pending,
                       count(*) filter (where status = 'PUBLISHED') as published
                  from flashcard_sets
                union all
                select 'QUIZ', count(*) filter (where status = 'DRAFT'),
                       count(*) filter (where status = 'PENDING_REVIEW'), count(*) filter (where status = 'PUBLISHED')
                  from quizzes
                union all
                select 'DICTATION_LESSON', count(*) filter (where status = 'DRAFT'),
                       count(*) filter (where status = 'PENDING_REVIEW'), count(*) filter (where status = 'PUBLISHED')
                  from dictation_lessons
                union all
                select 'SPEAKING_PROMPT', count(*) filter (where status = 'DRAFT'),
                       count(*) filter (where status = 'PENDING_REVIEW'), count(*) filter (where status = 'PUBLISHED')
                  from speaking_prompts
                union all
                select 'EXAM', count(*) filter (where status = 'DRAFT'),
                       count(*) filter (where status = 'PENDING_REVIEW'), count(*) filter (where status = 'PUBLISHED')
                  from exams
                """).query((rs, rowNum) -> new ContentCounts(rs.getString("kind"), rs.getLong("drafts"),
                rs.getLong("pending"), rs.getLong("published"))).list();
    }

    /**
     * Learners, and what they did since {@code from}. "Active" is anyone who reviewed a card, typed a sentence, or
     * finished a quiz or an exam - counted once however many of those they did.
     */
    public Activity activitySince(Instant from) {
        return jdbcClient.sql("""
                select
                  (select count(*) from users where role = 'LEARNER') as learners,
                  (select count(*) from users where role = 'LEARNER' and created_at >= :from) as new_learners,
                  (select count(distinct user_id) from (
                      select user_id from flashcard_review_logs where reviewed_at >= :from
                      union select user_id from dictation_attempts where attempted_at >= :from
                      union select user_id from quiz_attempts where submitted_at >= :from
                      union select user_id from exam_attempts where submitted_at >= :from
                   ) active) as active_learners,
                  (select count(*) from flashcard_review_logs where reviewed_at >= :from) as card_reviews,
                  (select count(*) from dictation_attempts where attempted_at >= :from) as dictation_sentences,
                  (select count(*) from quiz_attempts
                    where submitted_at >= :from and status = 'SCORED') as quizzes_submitted,
                  (select count(*) from exam_attempts
                    where submitted_at >= :from and status = 'SCORED') as exams_submitted
                """).param("from", SqlTime.at(from))
                .query((rs, rowNum) -> new Activity(rs.getLong("learners"), rs.getLong("new_learners"),
                        rs.getLong("active_learners"), rs.getLong("card_reviews"), rs.getLong("dictation_sentences"),
                        rs.getLong("quizzes_submitted"), rs.getLong("exams_submitted")))
                .single();
    }

    public record ContentCounts(String kind, long drafts, long pendingReview, long published) {
    }

    public record Activity(long learners, long newLearners, long activeLearners, long cardReviews,
            long dictationSentences, long quizzesSubmitted, long examsSubmitted) {
    }
}
