package com.englow3.ai.learningpath;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class LearningContentResolver {

    private final JdbcTemplate jdbcTemplate;

    LearningContentResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    ContentRef resolve(UUID userId, String conceptId, double mastery, String excludedType, String excludedId) {
        BigDecimal targetDifficulty = BigDecimal.valueOf(Math.max(0.15, Math.min(0.85, mastery + 0.15)));
        return jdbcTemplate.query("""
                with candidates as (
                    select 'FLASHCARD' content_type, f.id content_id, f.difficulty_prior difficulty
                    from flashcards f join flashcard_concepts link on link.flashcard_id = f.id
                    where link.concept_id = ? and f.review_status in
                        ('human_approved', 'human_verified', 'approved', 'published')
                    union all
                    select 'GRAMMAR', g.id, 0.45 from grammar_points g
                    join grammar_point_concepts link on link.grammar_point_id = g.id
                    where link.concept_id = ? and g.review_status in
                        ('human_approved', 'human_verified', 'approved', 'published')
                    union all
                    select 'SHADOWING', s.clip_id, 0.50 from shadowing_clips s
                    join shadowing_clip_concepts link on link.clip_id = s.clip_id
                    where link.concept_id = ? and s.review_status in ('human_approved', 'human_verified')
                    union all
                    select 'WRITING', w.task_id, w.difficulty_prior from writing_tasks w
                    join task_concepts link on link.task_id = w.task_id and link.task_kind = 'writing'
                    where link.concept_id = ? and w.review_status in
                        ('human_approved', 'human_verified', 'approved', 'published')
                    union all
                    select 'SPEAKING', s.task_id, s.difficulty_prior from speaking_tasks s
                    join task_concepts link on link.task_id = s.task_id and link.task_kind = 'speaking'
                    where link.concept_id = ? and s.review_status in
                        ('human_approved', 'human_verified', 'approved', 'published')
                    union all
                    select 'EXAM_ITEM', i.item_id, i.difficulty_prior from exam_items i
                    join exam_item_concepts link on link.item_id = i.item_id
                    where link.concept_id = ? and i.review_status in
                        ('human_approved', 'human_verified', 'approved', 'published')
                )
                select content_type, content_id, difficulty,
                       exists (select 1 from learning_events e where e.user_id = ?
                               and e.source_type = candidates.content_type
                               and e.source_id = candidates.content_id) used_before
                from candidates
                where not (content_type = coalesce(?, '') and content_id = coalesce(?, ''))
                order by used_before, abs(difficulty - ?), content_type, content_id
                limit 1
                """,
                rs -> rs.next()
                        ? new ContentRef(rs.getString("content_type"), rs.getString("content_id"),
                                rs.getBigDecimal("difficulty"), rs.getBoolean("used_before"))
                        : null,
                conceptId, conceptId, conceptId, conceptId, conceptId, conceptId, userId, excludedType, excludedId,
                targetDifficulty);
    }

    ContentRef resolve(UUID userId, String conceptId, double mastery) {
        return resolve(userId, conceptId, mastery, null, null);
    }

    record ContentRef(String type, String id, BigDecimal difficulty, boolean usedBefore) {
    }
}
