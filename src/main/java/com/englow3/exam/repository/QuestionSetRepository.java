package com.englow3.exam.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.QuestionSet;

/** See {@link ExamSectionRepository} for why the five content repositories exist. */
public interface QuestionSetRepository extends JpaRepository<QuestionSet, UUID> {

    /** Two levels up to the paper, joined by id in the subquery - see {@link SectionPartRepository}. */
    @Modifying
    @Query("""
            delete from QuestionSet qs where qs.sectionPartId in (
                select p.id from SectionPart p, ExamSection s
                where p.examSectionId = s.id and s.examId = :examId
            )
            """)
    void deleteAllForExam(@Param("examId") UUID examId);
}
