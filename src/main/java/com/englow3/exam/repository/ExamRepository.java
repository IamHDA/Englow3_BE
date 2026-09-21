package com.englow3.exam.repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    @Query("""
            select e from Exam e
            where (:status is null or e.status = :status)
              and (:examType is null or e.examType = :examType)
              and (:certificateType is null or e.certificateType = :certificateType)
              and (:certificateVariant is null or e.certificateVariant = :certificateVariant)
              and (:targetLevel is null or e.targetLevel = :targetLevel)
              and (:title is null or lower(e.title) like lower(concat('%', cast(:title as String), '%')))
            """)
    Page<Exam> searchCatalogue(@Param("status") ExamStatus status, @Param("examType") ExamType examType,
            @Param("certificateType") com.englow3.exam.entity.CertificateType certificateType,
            @Param("certificateVariant") com.englow3.exam.entity.CertificateVariant certificateVariant,
            @Param("targetLevel") com.englow3.exam.entity.TargetLevel targetLevel, @Param("title") String title,
            Pageable pageable);

    /**
     * The three figures {@code Exam.publish(...)} weighs, as three plain scalars. They were one projection record and
     * one round trip until the record turned out to be the only reason a persistence type crossed into the service, and
     * the only reason this file carried a constructor expression naming its own nested class. Publishing is a rare
     * admin action; two extra round trips buy back a type nobody needed.
     */
    @Query("select count(s) from ExamSection s where s.examId = :examId")
    long countSections(@Param("examId") UUID examId);

    @Query("""
            select count(q) from Question q, QuestionSet qs, SectionPart sp, ExamSection s
             where q.questionSetId = qs.id and qs.sectionPartId = sp.id
               and sp.examSectionId = s.id and s.examId = :examId
            """)
    long countQuestions(@Param("examId") UUID examId);

    /**
     * Batched count across all exams in a page to eliminate N+1 roundtrips.
     */
    @Query("""
            select s.examId, count(q)
              from Question q, QuestionSet qs, SectionPart sp, ExamSection s
             where q.questionSetId = qs.id and qs.sectionPartId = sp.id
               and sp.examSectionId = s.id and s.examId in :examIds
             group by s.examId
            """)
    List<Object[]> countQuestionsForExams(@Param("examIds") Collection<UUID> examIds);

    /**
     * The placement paper to hand a learner who does not know their level. Ordered by publication so the newest
     * published one wins, and {@code Optional} because a deployment with no placement paper is a normal state, not a
     * broken one.
     */
    Optional<Exam> findFirstByExamTypeAndStatusOrderByPublishedAtDesc(ExamType examType, ExamStatus status);

    @Query("select coalesce(sum(s.maxRawScore), 0) from ExamSection s where s.examId = :examId")
    BigDecimal sumSectionScores(@Param("examId") UUID examId);
}
