package com.englow3.exam.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.QuestionSetOption;

/** The lists question sets share. Deleted after the matching answers that point at them, before their sets. */
public interface QuestionSetOptionRepository extends JpaRepository<QuestionSetOption, UUID> {

    @Modifying
    @Query("""
            delete from QuestionSetOption o where o.questionSetId in (
                select qs.id from QuestionSet qs, SectionPart p, ExamSection s
                where qs.sectionPartId = p.id and p.examSectionId = s.id and s.examId = :examId
            )
            """)
    void deleteAllForExam(@Param("examId") UUID examId);

    @Query("select o from QuestionSetOption o where o.questionSetId in :setIds order by o.orderNo")
    List<QuestionSetOption> findAllForSets(@Param("setIds") Collection<UUID> setIds);
}
