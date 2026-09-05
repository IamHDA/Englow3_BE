package com.englow3.exam.query;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Repository;

import com.englow3.exam.dto.result.ExamDetailResult;
import com.englow3.exam.dto.result.ExamDetailResult.ExamSectionResult;
import com.englow3.exam.dto.result.ExamDetailResult.QuestionOptionResult;
import com.englow3.exam.dto.result.ExamDetailResult.QuestionResult;
import com.englow3.exam.dto.result.ExamDetailResult.QuestionSetResult;
import com.englow3.exam.dto.result.ExamDetailResult.SectionPartResult;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamSection;
import com.englow3.exam.entity.Question;
import com.englow3.exam.entity.QuestionOption;
import com.englow3.exam.entity.QuestionSet;
import com.englow3.exam.entity.SectionPart;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Loads a whole paper. Read-only, and every table it touches is exam-owned, so it needs no cross-module read exception.
 * Five flat queries assembled in memory rather than one query with five {@code join fetch}es: fetching more than one
 * collection level either raises MultipleBagFetchException or multiplies rows into a cartesian product, and the row
 * count of a full TOEIC paper makes that expensive rather than merely wrong.
 */
@Repository
@RequiredArgsConstructor
public class AdminExamPaperQuery {

    private final EntityManager em;

    /**
     * The admin projection: answer keys and explanations included. It is what the detail screen and its printed form
     * both read. The sitting owes a second descent over the same tables with those left out - deliberately not this
     * method with a flag, because a flag is one edit away from leaking an answer key into a paper being sat.
     */
    public Optional<ExamDetailResult> loadForAdmin(UUID examId) {
        Exam exam = em.find(Exam.class, examId);
        if (exam == null) {
            return Optional.empty();
        }

        List<ExamSection> sections = em
                .createQuery("select s from ExamSection s where s.examId = :examId order by s.orderNo",
                        ExamSection.class)
                .setParameter("examId", examId).getResultList();
        List<SectionPart> parts = childrenOf(idsOf(sections, ExamSection::getId),
                "select p from SectionPart p where p.examSectionId in :parentIds order by p.orderNo",
                SectionPart.class);
        List<QuestionSet> questionSets = childrenOf(idsOf(parts, SectionPart::getId),
                "select qs from QuestionSet qs where qs.sectionPartId in :parentIds order by qs.orderNo",
                QuestionSet.class);
        List<Question> questions = childrenOf(idsOf(questionSets, QuestionSet::getId),
                "select q from Question q where q.questionSetId in :parentIds order by q.orderNo", Question.class);
        List<QuestionOption> options = childrenOf(idsOf(questions, Question::getId),
                "select o from QuestionOption o where o.questionId in :parentIds order by o.orderNo",
                QuestionOption.class);

        return Optional.of(assemble(exam, sections, parts, questionSets, questions, options));
    }

    /**
     * Bottom up, so each level is built once and looked up by its parent id. groupingBy keeps the encounter order of
     * the stream, and every query above is ordered by {@code order_no}, so the children of a parent stay in the order
     * the paper puts them.
     */
    private ExamDetailResult assemble(Exam exam, List<ExamSection> sections, List<SectionPart> parts,
            List<QuestionSet> questionSets, List<Question> questions, List<QuestionOption> options) {
        Map<UUID, List<QuestionOptionResult>> optionsByQuestion = options.stream()
                .collect(groupingBy(QuestionOption::getQuestionId, mapping(QuestionOptionResult::of, toList())));

        Map<UUID, List<QuestionResult>> questionsBySet = questions.stream().collect(groupingBy(
                Question::getQuestionSetId,
                mapping(q -> QuestionResult.of(q, optionsByQuestion.getOrDefault(q.getId(), List.of())), toList())));

        Map<UUID, List<QuestionSetResult>> setsByPart = questionSets.stream().collect(groupingBy(
                QuestionSet::getSectionPartId,
                mapping(qs -> QuestionSetResult.of(qs, questionsBySet.getOrDefault(qs.getId(), List.of())), toList())));

        Map<UUID, List<SectionPartResult>> partsBySection = parts.stream().collect(groupingBy(
                SectionPart::getExamSectionId,
                mapping(p -> SectionPartResult.of(p, setsByPart.getOrDefault(p.getId(), List.of())), toList())));

        return ExamDetailResult.of(exam, sections.stream()
                .map(s -> ExamSectionResult.of(s, partsBySection.getOrDefault(s.getId(), List.of()))).toList());
    }

    /** An empty parent set must not reach the query: {@code in ()} is not valid SQL. */
    private <T> List<T> childrenOf(List<UUID> parentIds, String jpql, Class<T> type) {
        return parentIds.isEmpty() ? List.of()
                : em.createQuery(jpql, type).setParameter("parentIds", parentIds).getResultList();
    }

    private static <T> List<UUID> idsOf(List<T> rows, Function<T, UUID> id) {
        return rows.stream().map(id).toList();
    }
}
