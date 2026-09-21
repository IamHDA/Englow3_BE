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

import com.englow3.exam.dto.result.LearnerExamPaperResult;
import com.englow3.exam.dto.result.LearnerExamPaperResult.ExamSectionResult;
import com.englow3.exam.dto.result.LearnerExamPaperResult.QuestionOptionResult;
import com.englow3.exam.dto.result.LearnerExamPaperResult.QuestionResult;
import com.englow3.exam.dto.result.LearnerExamPaperResult.QuestionSetResult;
import com.englow3.exam.dto.result.LearnerExamPaperResult.SectionPartResult;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamSection;
import com.englow3.exam.entity.Question;
import com.englow3.exam.entity.QuestionOption;
import com.englow3.exam.entity.QuestionSet;
import com.englow3.exam.entity.SectionPart;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** Loads the learner projection separately so answer-key fields cannot accidentally reach a sitting response. */
@Repository
@RequiredArgsConstructor
public class LearnerExamPaperQuery {

    private final EntityManager em;

    public Optional<LearnerExamPaperResult> load(UUID examId) {
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
        List<QuestionSet> sets = childrenOf(idsOf(parts, SectionPart::getId),
                "select qs from QuestionSet qs where qs.sectionPartId in :parentIds order by qs.orderNo",
                QuestionSet.class);
        List<Question> questions = childrenOf(idsOf(sets, QuestionSet::getId),
                "select q from Question q where q.questionSetId in :parentIds order by q.orderNo", Question.class);
        List<QuestionOption> options = childrenOf(idsOf(questions, Question::getId),
                "select o from QuestionOption o where o.questionId in :parentIds order by o.orderNo",
                QuestionOption.class);

        Map<UUID, List<QuestionOptionResult>> optionsByQuestion = options.stream()
                .collect(groupingBy(QuestionOption::getQuestionId, mapping(QuestionOptionResult::of, toList())));
        Map<UUID, List<QuestionResult>> questionsBySet = questions.stream().collect(groupingBy(
                Question::getQuestionSetId,
                mapping(q -> QuestionResult.of(q, optionsByQuestion.getOrDefault(q.getId(), List.of())), toList())));
        Map<UUID, List<QuestionSetResult>> setsByPart = sets.stream()
                .collect(groupingBy(QuestionSet::getSectionPartId,
                        mapping(set -> QuestionSetResult.of(set, questionsBySet.getOrDefault(set.getId(), List.of())),
                                toList())));
        Map<UUID, List<SectionPartResult>> partsBySection = parts.stream()
                .collect(groupingBy(SectionPart::getExamSectionId,
                        mapping(part -> SectionPartResult.of(part, setsByPart.getOrDefault(part.getId(), List.of())),
                                toList())));

        return Optional.of(LearnerExamPaperResult.of(exam, sections.stream()
                .map(section -> ExamSectionResult.of(section, partsBySection.getOrDefault(section.getId(), List.of())))
                .toList()));
    }

    private <T> List<T> childrenOf(List<UUID> parentIds, String jpql, Class<T> type) {
        return parentIds.isEmpty() ? List.of()
                : em.createQuery(jpql, type).setParameter("parentIds", parentIds).getResultList();
    }

    private static <T> List<UUID> idsOf(List<T> rows, Function<T, UUID> id) {
        return rows.stream().map(id).toList();
    }
}
