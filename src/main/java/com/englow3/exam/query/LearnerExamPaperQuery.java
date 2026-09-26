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
import com.englow3.exam.dto.projection.LearnerExamPaperProjection;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection.Option;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection.Part;
import com.englow3.exam.dto.projection.LearnerExamPaperProjection.Section;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/** Loads a learner-safe paper projection separately so answer keys cannot reach a sitting response. */
@Repository
@RequiredArgsConstructor
public class LearnerExamPaperQuery {

    private final EntityManager em;

    public Optional<LearnerExamPaperProjection> load(UUID examId) {
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

        Map<UUID, List<Option>> optionsByQuestion = options.stream().collect(groupingBy(QuestionOption::getQuestionId,
                mapping(option -> new Option(option.getId(), option.getContent(), option.getOrderNo()), toList())));
        Map<UUID, List<LearnerExamPaperProjection.Question>> questionsBySet = questions.stream().collect(groupingBy(
                Question::getQuestionSetId,
                mapping(q -> new LearnerExamPaperProjection.Question(q.getId(), q.getQuestionType(), q.getContent(),
                        q.getDifficultyLevel(), q.getSkillType(), q.getQuestionCategory(), q.getOrderNo(),
                        q.getMaxRawScore(), optionsByQuestion.getOrDefault(q.getId(), List.of())), toList())));
        Map<UUID, List<LearnerExamPaperProjection.QuestionSet>> setsByPart = sets.stream().collect(groupingBy(
                QuestionSet::getSectionPartId,
                mapping(set -> new LearnerExamPaperProjection.QuestionSet(set.getId(), set.getTitle(),
                        set.getInstruction(), set.getOrderNo(), set.getContent(), set.getAudioObjectKey(),
                        set.getImageObjectKey(), questionsBySet.getOrDefault(set.getId(), List.of())), toList())));
        Map<UUID, List<Part>> partsBySection = parts.stream().collect(groupingBy(SectionPart::getExamSectionId,
                mapping(part -> new Part(part.getId(), part.getOrderNo(), part.getTitle(), part.getInstruction(),
                        part.getContent(), part.getAudioObjectKey(), part.getImageObjectKey(),
                        setsByPart.getOrDefault(part.getId(), List.of())), toList())));

        return Optional.of(new LearnerExamPaperProjection(exam,
                sections.stream()
                        .map(section -> new Section(section.getId(), section.getSectionType(), section.getOrderNo(),
                                section.getMaxRawScore(), section.isScoredByCriteria(), section.getTimeLimitSeconds(),
                                partsBySection.getOrDefault(section.getId(), List.of())))
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
