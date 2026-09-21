package com.englow3.exam.query;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.englow3.exam.entity.Question;
import com.englow3.exam.entity.QuestionOption;
import com.englow3.exam.entity.QuestionType;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** Server-only grading material. No controller serializes these records directly. */
@Repository
@RequiredArgsConstructor
public class ExamGradingQuery {

    private final EntityManager em;

    public record GradingOption(UUID id, boolean correct, String explanation) {
        static GradingOption of(QuestionOption option) {
            return new GradingOption(option.getId(), option.isCorrect(), option.getExplanation());
        }
    }

    public record GradingQuestion(UUID id, QuestionType questionType, BigDecimal maxRawScore, String explanation,
            List<GradingOption> options) {
    }

    public List<GradingQuestion> load(UUID examId) {
        List<Question> questions = em.createQuery("""
                select q from Question q, QuestionSet qs, SectionPart p, ExamSection s
                 where q.questionSetId = qs.id and qs.sectionPartId = p.id
                   and p.examSectionId = s.id and s.examId = :examId
                 order by s.orderNo, p.orderNo, qs.orderNo, q.orderNo
                """, Question.class).setParameter("examId", examId).getResultList();
        if (questions.isEmpty()) {
            return List.of();
        }

        List<UUID> questionIds = questions.stream().map(Question::getId).toList();
        List<QuestionOption> options = em
                .createQuery("select o from QuestionOption o where o.questionId in :questionIds order by o.orderNo",
                        QuestionOption.class)
                .setParameter("questionIds", questionIds).getResultList();
        Map<UUID, List<GradingOption>> optionsByQuestion = options.stream()
                .collect(groupingBy(QuestionOption::getQuestionId, mapping(GradingOption::of, toList())));

        return questions.stream()
                .map(question -> new GradingQuestion(question.getId(), question.getQuestionType(),
                        question.getMaxRawScore(), question.getExplanation(),
                        optionsByQuestion.getOrDefault(question.getId(), List.of())))
                .toList();
    }
}
