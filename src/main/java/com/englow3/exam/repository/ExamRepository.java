package com.englow3.exam.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;

public interface ExamRepository extends JpaRepository<Exam, UUID> {

    /**
     * Entities rather than a projection: the row has no collection to lazy-load and one page is a handful of them, so
     * the projection would only buy a hand-written count query. ponytail: three null-guard filters inline. Move to
     * exam/query/ with composed conditions when the catalogue holds more than one certificate and filtering by type /
     * variant / level starts to mean something. The {@code cast(:title as String)} is load-bearing, not noise. Without
     * it a null title reaches Postgres as an untyped parameter, it infers {@code bytea}, and the statement dies on
     * "function lower(bytea) does not exist" - which is every unfiltered call to this list. The enum parameters need no
     * cast: each is compared to a typed column in the same expression, so Postgres infers them from that.
     */
    @Query("""
            select e from Exam e
            where (:status is null or e.status = :status)
              and (:examType is null or e.examType = :examType)
              and (:title is null or lower(e.title) like lower(concat('%', cast(:title as String), '%')))
            """)
    Page<Exam> search(@Param("status") ExamStatus status, @Param("examType") ExamType examType,
            @Param("title") String title, Pageable pageable);

    /**
     * What {@code Exam.publish(...)} weighs, and what the admin list shows per row - one query for both, so the list
     * costs one extra round trip rather than one per row. Native because the tables below `exams` have no entity yet;
     * content is still seeded by SQL. Correlated subqueries rather than joins on purpose: joining sections to questions
     * multiplies the section rows, which would leave {@code sum(max_raw_score)} silently too large - the one figure
     * {@code publish()} compares against. {@code {h-schema\}} expands to the configured default schema; a native query
     * gets no schema applied for it.
     */
    @Query(nativeQuery = true, value = """
            select e.id as "examId",
                   (select count(*) from {h-schema}exam_sections s
                     where s.exam_id = e.id) as "sectionCount",
                   (select count(*) from {h-schema}questions q
                     join {h-schema}question_sets qs on qs.id = q.question_set_id
                     join {h-schema}section_parts sp on sp.id = qs.section_part_id
                     join {h-schema}exam_sections s on s.id = sp.exam_section_id
                     where s.exam_id = e.id) as "questionCount",
                   (select coalesce(sum(s.max_raw_score), 0) from {h-schema}exam_sections s
                     where s.exam_id = e.id) as "sectionsRawTotal"
              from {h-schema}exams e
             where e.id in (:examIds)
            """)
    List<ExamContentTotals> contentTotals(@Param("examIds") Collection<UUID> examIds);
}
