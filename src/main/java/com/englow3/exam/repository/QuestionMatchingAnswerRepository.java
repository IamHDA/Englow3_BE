package com.englow3.exam.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.QuestionMatchingAnswer;

/** The first content rows to delete: they point at both a question and a set's list entry. */
public interface QuestionMatchingAnswerRepository
        extends JpaRepository<QuestionMatchingAnswer, QuestionMatchingAnswer.Key> {

    @Modifying
    @Query("""
            delete from QuestionMatchingAnswer a where a.key.questionId in (
                select q.id from Question q, QuestionSet qs, SectionPart p, ExamSection s
                where q.questionSetId = qs.id and qs.sectionPartId = p.id
                  and p.examSectionId = s.id and s.examId = :examId
            )
            """)
    void deleteAllForExam(@Param("examId") java.util.UUID examId);

    @Query("select a from QuestionMatchingAnswer a where a.key.questionId in :questionIds")
    List<QuestionMatchingAnswer> findAllForQuestions(@Param("questionIds") Collection<java.util.UUID> questionIds);
}
