package com.englow3.exam.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.Question;
import com.englow3.exam.entity.SkillType;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    /**
     * The question bank: every question ever authored, searchable. Entities rather than a projection, the same
     * reasoning as {@code ExamRepository.search} - a page is a handful of rows and none of them lazy-loads a
     * collection. The {@code cast(:keyword as String)} is load-bearing for the same reason it is there: an unfiltered
     * call sends a null parameter, Postgres infers it as {@code bytea} with no cast, and {@code lower(bytea)} does not
     * exist.
     */
    @Query("""
            select q from Question q
            where (:skillType is null or q.skillType = :skillType)
              and (:difficultyLevel is null or q.difficultyLevel = :difficultyLevel)
              and (:keyword is null or lower(q.content) like lower(concat('%', cast(:keyword as String), '%')))
            """)
    Page<Question> search(@Param("skillType") SkillType skillType,
            @Param("difficultyLevel") DifficultyLevel difficultyLevel, @Param("keyword") String keyword,
            Pageable pageable);

    /** See {@link ExamSectionRepository} - deleted fourth, after the options hanging off these rows. */
    @Modifying
    @Query("""
            delete from Question q where q.questionSetId in (
                select qs.id from QuestionSet qs, SectionPart p, ExamSection s
                where qs.sectionPartId = p.id and p.examSectionId = s.id and s.examId = :examId
            )
            """)
    void deleteAllForExam(@Param("examId") UUID examId);

    /**
     * The {@code order_no} of every question under a paper that {@code Exam.publish(...)} would refuse: no option, no
     * correct option, or - only for SINGLE_CHOICE, where MULTIPLE_CHOICE is allowed more than one - more than one
     * correct option. Three correlated subqueries per row rather than one join, because joining brings the option rows
     * in as a multiplier, which is exactly the fan-out bug {@code ExamRepository.sumSectionScores} already exists to
     * avoid one level up. Publishing is a rare admin action, so the extra subqueries are the cheap side of that trade.
     */
    @Query("""
            select q.orderNo from Question q
            where q.id in (
                select q2.id from Question q2, QuestionSet qs, SectionPart sp, ExamSection s
                where q2.questionSetId = qs.id and qs.sectionPartId = sp.id
                  and sp.examSectionId = s.id and s.examId = :examId
            )
            and (
                (select count(o) from QuestionOption o where o.questionId = q.id) = 0
                or (select count(o) from QuestionOption o where o.questionId = q.id and o.correct = true) = 0
                or (q.questionType = com.englow3.exam.entity.QuestionType.SINGLE_CHOICE
                    and (select count(o) from QuestionOption o where o.questionId = q.id and o.correct = true) > 1)
            )
            order by q.orderNo
            """)
    List<Integer> findIncompleteQuestionOrderNos(@Param("examId") UUID examId);
}
