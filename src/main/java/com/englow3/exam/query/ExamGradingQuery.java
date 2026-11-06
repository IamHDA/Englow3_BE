package com.englow3.exam.query;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.englow3.exam.entity.Question;
import com.englow3.exam.entity.QuestionAcceptedAnswer;
import com.englow3.exam.entity.QuestionOption;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.helper.AnswerKey;

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

    /** One accepted spelling of a TEXT_ENTRY question. */
    public record GradingAcceptedAnswer(String text, boolean caseSensitive) {
        static GradingAcceptedAnswer of(QuestionAcceptedAnswer answer) {
            return new GradingAcceptedAnswer(answer.getAnswerText(), answer.isCaseSensitive());
        }
    }

    public record GradingQuestion(UUID id, QuestionType questionType, BigDecimal maxRawScore, String explanation,
            List<GradingOption> options, UUID sectionId, SectionType sectionType,
            List<GradingAcceptedAnswer> acceptedAnswers) {

        public AnswerKey key() {
            return new AnswerKey(questionType, maxRawScore,
                    options.stream().filter(GradingOption::correct).map(GradingOption::id).collect(toSet()),
                    acceptedAnswers.stream().map(a -> new AnswerKey.Accepted(a.text(), a.caseSensitive())).toList());
        }
    }

    public List<GradingQuestion> load(UUID examId) {
        return grade(em.createQuery("""
                select q, s.id, s.sectionType from Question q, QuestionSet qs, SectionPart p, ExamSection s
                 where q.questionSetId = qs.id and qs.sectionPartId = p.id
                   and p.examSectionId = s.id and s.examId = :examId
                 order by s.orderNo, p.orderNo, qs.orderNo, q.orderNo
                """, Object[].class).setParameter("examId", examId).getResultList());
    }

    /** Only the questions in these parts of the paper - what a practice is graded on. */
    public List<GradingQuestion> load(UUID examId, Collection<UUID> partIds) {
        return grade(em.createQuery("""
                select q, s.id, s.sectionType from Question q, QuestionSet qs, SectionPart p, ExamSection s
                 where q.questionSetId = qs.id and qs.sectionPartId = p.id
                   and p.examSectionId = s.id and s.examId = :examId and p.id in :partIds
                 order by s.orderNo, p.orderNo, qs.orderNo, q.orderNo
                """, Object[].class).setParameter("examId", examId).setParameter("partIds", partIds).getResultList());
    }

    private List<GradingQuestion> grade(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> questionIds = rows.stream().map(row -> ((Question) row[0]).getId()).toList();
        List<QuestionOption> options = em
                .createQuery("select o from QuestionOption o where o.questionId in :questionIds order by o.orderNo",
                        QuestionOption.class)
                .setParameter("questionIds", questionIds).getResultList();
        Map<UUID, List<GradingOption>> optionsByQuestion = options.stream()
                .collect(groupingBy(QuestionOption::getQuestionId, mapping(GradingOption::of, toList())));
        List<QuestionAcceptedAnswer> accepted = em.createQuery(
                "select a from QuestionAcceptedAnswer a where a.questionId in :questionIds order by a.orderNo",
                QuestionAcceptedAnswer.class).setParameter("questionIds", questionIds).getResultList();
        Map<UUID, List<GradingAcceptedAnswer>> acceptedByQuestion = accepted.stream().collect(
                groupingBy(QuestionAcceptedAnswer::getQuestionId, mapping(GradingAcceptedAnswer::of, toList())));

        return rows.stream().map(row -> {
            Question question = (Question) row[0];
            return new GradingQuestion(question.getId(), question.getQuestionType(), question.getMaxRawScore(),
                    question.getExplanation(), optionsByQuestion.getOrDefault(question.getId(), List.of()),
                    (UUID) row[1], (SectionType) row[2], acceptedByQuestion.getOrDefault(question.getId(), List.of()));
        }).toList();
    }
}
