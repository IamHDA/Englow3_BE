package com.englow3.exam.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.QuestionOption;

/** See {@link ExamSectionRepository} for why the five content repositories exist. */
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {

    /** The deepest descent, and the first delete to run - every other content row is a parent of these. */
    @Modifying
    @Query("""
            delete from QuestionOption o where o.questionId in (
                select q.id from Question q, QuestionSet qs, SectionPart p, ExamSection s
                where q.questionSetId = qs.id and qs.sectionPartId = p.id
                  and p.examSectionId = s.id and s.examId = :examId
            )
            """)
    void deleteAllForExam(@Param("examId") UUID examId);

    /** Options for a page of question-bank results, fetched for the whole id set rather than one question at a time. */
    @Query("select o from QuestionOption o where o.questionId in :questionIds order by o.orderNo")
    List<QuestionOption> findAllForQuestions(@Param("questionIds") List<UUID> questionIds);
}
