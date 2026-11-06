package com.englow3.exam.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.QuestionAcceptedAnswer;

/** Accepted spellings of TEXT_ENTRY questions. Deleted with the options, before the questions they belong to. */
public interface QuestionAcceptedAnswerRepository extends JpaRepository<QuestionAcceptedAnswer, UUID> {

    @Modifying
    @Query("""
            delete from QuestionAcceptedAnswer a where a.questionId in (
                select q.id from Question q, QuestionSet qs, SectionPart p, ExamSection s
                where q.questionSetId = qs.id and qs.sectionPartId = p.id
                  and p.examSectionId = s.id and s.examId = :examId
            )
            """)
    void deleteAllForExam(@Param("examId") UUID examId);

    @Query("select a from QuestionAcceptedAnswer a where a.questionId in :questionIds order by a.orderNo")
    List<QuestionAcceptedAnswer> findAllForQuestions(@Param("questionIds") Collection<UUID> questionIds);
}
