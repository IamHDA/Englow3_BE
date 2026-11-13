package com.englow3.roadmap.query;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.englow3.roadmap.entity.RoadmapContentKind;

import lombok.RequiredArgsConstructor;

/**
 * How far a learner has got with scheduled content, 0-100, worked out from their activity on every read. A declared,
 * read-only cross-module read of the activity tables (recorded in {@code docs/module-map.md}); the roadmap stores no
 * progress of its own, so it can never disagree with what the learner did.
 * <ul>
 * <li>Flashcard set: share of its cards reviewed at least once.</li>
 * <li>Dictation lesson: share of its sentences attempted.</li>
 * <li>Quiz: best scored percentage.</li>
 * <li>Speaking prompt: best pronunciation score.</li>
 * <li>Writing/speaking task: 100 once an answer is submitted and not failed.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ItemProgressQuery {

    private final JdbcClient jdbc;

    public Map<UUID, Integer> percentDone(UUID userId, Map<RoadmapContentKind, ? extends Collection<UUID>> ids) {
        Map<UUID, Integer> done = new HashMap<>();
        ids.forEach((kind, contentIds) -> {
            if (!contentIds.isEmpty()) {
                read(sql(kind), userId, contentIds).forEach(row -> done.put(row.id(), row.percent()));
            }
        });
        return done;
    }

    private record Row(UUID id, int percent) {
    }

    private List<Row> read(String sql, UUID userId, Collection<UUID> ids) {
        return jdbc.sql(sql).param("userId", userId).param("ids", ids)
                .query((rs, n) -> new Row(rs.getObject("id", UUID.class),
                        Math.max(0, Math.min(100, (int) Math.floor(rs.getDouble("percent"))))))
                .list();
    }

    private static String sql(RoadmapContentKind kind) {
        return switch (kind) {
            case FLASHCARD_SET -> """
                    select f.flashcard_set_id as id,
                           100.0 * count(r.id) / nullif(count(*), 0) as percent
                      from flashcards f
                      left join flashcard_reviews r
                        on r.flashcard_id = f.id and r.user_id = :userId and r.last_reviewed_at is not null
                     where f.flashcard_set_id in (:ids)
                     group by f.flashcard_set_id
                    """;
            case DICTATION_LESSON -> """
                    select s.dictation_lesson_id as id,
                           100.0 * count(distinct a.dictation_sentence_id) / nullif(count(distinct s.id), 0) as percent
                      from dictation_sentences s
                      left join dictation_attempts a on a.dictation_sentence_id = s.id and a.user_id = :userId
                     where s.dictation_lesson_id in (:ids)
                     group by s.dictation_lesson_id
                    """;
            case QUIZ -> """
                    select quiz_id as id, max(score_percentage) as percent from quiz_attempts
                     where user_id = :userId and status = 'SCORED' and quiz_id in (:ids)
                     group by quiz_id
                    """;
            case SPEAKING_PROMPT -> """
                    select speaking_prompt_id as id, max(pronunciation_percent) as percent from speaking_attempts
                     where user_id = :userId and status = 'ASSESSED' and speaking_prompt_id in (:ids)
                     group by speaking_prompt_id
                    """;
            case ASSESSMENT_TASK -> """
                    select task_id as id, 100 as percent from assessment_attempts
                     where user_id = :userId and status in ('QUEUED', 'NEEDS_REVIEW', 'COMPLETED')
                       and task_id in (:ids)
                     group by task_id
                    """;
        };
    }
}
