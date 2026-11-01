package com.englow3.roadmap.query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.englow3.roadmap.entity.CefrLevel;
import com.englow3.roadmap.entity.RoadmapContentKind;
import com.englow3.roadmap.helper.RoadmapPlan.Candidate;
import com.englow3.roadmap.helper.RoadmapPlan.MockPaper;

import lombok.RequiredArgsConstructor;

/**
 * Published content a roadmap may schedule. A declared, read-only cross-module read of {@code flashcard_sets},
 * {@code dictation_lessons}, {@code quizzes}, {@code speaking_prompts}, {@code assessment_tasks} and {@code exams}
 * (recorded in {@code docs/module-map.md}), in the manner of the progress module's daily path: one catalogue across six
 * modules is one query here rather than six contracts that would each answer a single caller. Nothing here writes.
 * <p>
 * Levels compare as text because the bands sort that way ('A1' &lt; 'A2' &lt; 'B1' ...).
 */
@Component
@RequiredArgsConstructor
public class ContentCatalogQuery {

    private final JdbcClient jdbc;

    /**
     * Up to {@code perKind} pieces of each kind between {@code from} and {@code to}, best-fitting level first. Content
     * with no level is offered after levelled content as general practice.
     */
    public List<Candidate> candidates(CefrLevel from, CefrLevel to, String certificate, int perKind) {
        return jdbc.sql("""
                (select 'FLASHCARD_SET' as kind, s.id, s.name as title, s.target_level as level, 'VOCABULARY' as skill,
                        least(30, greatest(5, (select count(*) from flashcards f where f.flashcard_set_id = s.id) / 2))
                          as minutes
                   from flashcard_sets s
                  where s.status = 'PUBLISHED' and (s.target_level is null or s.target_level between :from and :to)
                  order by s.target_level is null, s.target_level, s.published_at desc nulls last
                  limit :perKind)
                union all
                (select 'DICTATION_LESSON', d.id, d.title, d.target_level, 'LISTENING',
                        least(30, greatest(5, (select count(*) from dictation_sentences x
                                                where x.dictation_lesson_id = d.id)))
                   from dictation_lessons d
                  where d.status = 'PUBLISHED' and (d.target_level is null or d.target_level between :from and :to)
                  order by d.target_level is null, d.target_level, d.published_at desc nulls last
                  limit :perKind)
                union all
                (select 'QUIZ', q.id, q.title, q.target_level, q.category,
                        least(60, greatest(5, coalesce(q.time_limit_seconds / 60, 15)))
                   from quizzes q
                  where q.status = 'PUBLISHED' and (q.target_level is null or q.target_level between :from and :to)
                  order by q.target_level is null, q.target_level, q.published_at desc nulls last
                  limit :perKind)
                union all
                (select 'SPEAKING_PROMPT', p.id, p.title, p.target_level, 'PRONUNCIATION', 5
                   from speaking_prompts p
                  where p.status = 'PUBLISHED' and (p.target_level is null or p.target_level between :from and :to)
                  order by p.target_level is null, p.target_level, p.published_at desc nulls last
                  limit :perKind)
                union all
                (select 'ASSESSMENT_TASK', t.id, t.title, t.target_level, t.skill,
                        least(60, greatest(10, coalesce(t.time_limit_seconds / 60, 30)))
                   from assessment_tasks t
                  where t.status = 'PUBLISHED' and t.purpose = 'PRACTICE'
                    and (t.target_level is null or t.target_level between :from and :to)
                    and (t.certificate_type is null or t.certificate_type = :certificate)
                  order by t.target_level is null, t.target_level, t.published_at desc nulls last
                  limit :perKind)
                """).param("from", from.name()).param("to", to.name()).param("certificate", certificate)
                .param("perKind", perKind)
                .query((rs, row) -> new Candidate(RoadmapContentKind.valueOf(rs.getString("kind")),
                        rs.getObject("id", UUID.class), rs.getString("title"),
                        CefrLevel.parse(rs.getString("level")).orElse(null), rs.getString("skill"),
                        rs.getInt("minutes")))
                .list();
    }

    /** Published mock papers of a certificate, newest first. */
    public List<MockPaper> mockPapers(String certificate) {
        return jdbc.sql("""
                select id, title, certificate_variant from exams
                 where status = 'PUBLISHED' and exam_type = 'MOCK' and certificate_type = :certificate
                 order by published_at desc nulls last
                 limit 20
                """).param("certificate", certificate).query((rs, row) -> new MockPaper(rs.getObject("id", UUID.class),
                rs.getString("title"), rs.getString("certificate_variant"))).list();
    }

    /** A published level-check paper, one the learner has not sat before where there is one. */
    public Optional<UUID> levelCheckPaper(Collection<UUID> alreadyUsed) {
        List<UUID> papers = jdbc.sql("""
                select id from exams where status = 'PUBLISHED' and exam_type = 'LEVEL_CHECK'
                 order by published_at desc nulls last limit 50
                """).query(UUID.class).list();
        return freshest(papers, alreadyUsed);
    }

    /** A published spoken level-check task, one the learner has not answered before where there is one. */
    public Optional<UUID> levelCheckSpeakingTask(Collection<UUID> alreadyUsed) {
        List<UUID> tasks = jdbc.sql("""
                select id from assessment_tasks
                 where status = 'PUBLISHED' and purpose = 'LEVEL_CHECK' and skill = 'SPEAKING'
                 order by published_at desc nulls last limit 50
                """).query(UUID.class).list();
        return freshest(tasks, alreadyUsed);
    }

    private static Optional<UUID> freshest(List<UUID> newestFirst, Collection<UUID> alreadyUsed) {
        return newestFirst.stream().filter(id -> !alreadyUsed.contains(id)).findFirst()
                .or(() -> newestFirst.stream().findFirst());
    }
}
