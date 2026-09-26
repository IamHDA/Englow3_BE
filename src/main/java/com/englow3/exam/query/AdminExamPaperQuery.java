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

import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamSection;
import com.englow3.exam.entity.Question;
import com.englow3.exam.entity.QuestionOption;
import com.englow3.exam.entity.QuestionSet;
import com.englow3.exam.entity.SectionPart;
import com.englow3.exam.dto.projection.AdminExamPaperProjection;
import com.englow3.exam.dto.projection.AdminExamPaperProjection.Option;
import com.englow3.exam.dto.projection.AdminExamPaperProjection.Part;
import com.englow3.exam.dto.projection.AdminExamPaperProjection.Section;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** Loads the admin paper as a query-scoped projection that still contains raw media object keys. */
@Repository
@RequiredArgsConstructor
public class AdminExamPaperQuery {

    private final EntityManager em;

    public Optional<AdminExamPaperProjection> loadForAdmin(UUID examId) {
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

    private AdminExamPaperProjection assemble(Exam exam, List<ExamSection> sections, List<SectionPart> parts,
            List<QuestionSet> questionSets, List<Question> questions, List<QuestionOption> options) {
        Map<UUID, List<Option>> optionsByQuestion = options.stream()
                .collect(groupingBy(QuestionOption::getQuestionId,
                        mapping(option -> new Option(option.getId(), option.getContent(), option.getOrderNo(),
                                option.isCorrect(), option.getExplanation()), toList())));

        Map<UUID, List<AdminExamPaperProjection.Question>> questionsBySet = questions.stream()
                .collect(groupingBy(Question::getQuestionSetId,
                        mapping(q -> new AdminExamPaperProjection.Question(q.getId(), q.getQuestionType(),
                                q.getContent(), q.getDifficultyLevel(), q.getSkillType(), q.getQuestionCategory(),
                                q.getOrderNo(), q.getMaxRawScore(), q.getExplanation(), q.getSourceQuestionId(),
                                optionsByQuestion.getOrDefault(q.getId(), List.of())), toList())));

        Map<UUID, List<AdminExamPaperProjection.QuestionSet>> setsByPart = questionSets.stream().collect(groupingBy(
                QuestionSet::getSectionPartId,
                mapping(qs -> new AdminExamPaperProjection.QuestionSet(qs.getId(), qs.getTitle(), qs.getInstruction(),
                        qs.getOrderNo(), qs.getContent(), qs.getAudioObjectKey(), qs.getImageObjectKey(),
                        qs.getSourceQuestionSetId(), questionsBySet.getOrDefault(qs.getId(), List.of())), toList())));

        Map<UUID, List<Part>> partsBySection = parts.stream().collect(groupingBy(SectionPart::getExamSectionId,
                mapping(part -> new Part(part.getId(), part.getOrderNo(), part.getTitle(), part.getInstruction(),
                        part.getContent(), part.getAudioObjectKey(), part.getImageObjectKey(),
                        setsByPart.getOrDefault(part.getId(), List.of())), toList())));

        return new AdminExamPaperProjection(exam,
                sections.stream()
                        .map(section -> new Section(section.getId(), section.getSectionType(), section.getOrderNo(),
                                section.getMaxRawScore(), section.isScoredByCriteria(), section.getTimeLimitSeconds(),
                                partsBySection.getOrDefault(section.getId(), List.of())))
                        .toList());
    }

    private <T> List<T> childrenOf(List<UUID> parentIds, String jpql, Class<T> type) {
        return parentIds.isEmpty() ? List.of()
                : em.createQuery(jpql, type).setParameter("parentIds", parentIds).getResultList();
    }

    private static <T> List<UUID> idsOf(List<T> rows, Function<T, UUID> id) {
        return rows.stream().map(id).toList();
    }

}
