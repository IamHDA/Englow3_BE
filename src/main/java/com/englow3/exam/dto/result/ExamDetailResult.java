package com.englow3.exam.dto.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamSection;
import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.SectionPart;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.entity.SkillType;
import com.englow3.exam.entity.TargetLevel;

/**
 * The whole paper, answer keys and explanations included. This is the admin projection; the sitting's tree load owes
 * the same descent with those left out. The section, part, question-set and question levels below are nested rather
 * than four files of their own: each is the field type of the level above it and nothing else refers to them. Nesting
 * also lets the whole tree be read top to bottom here. They stay public - the sitting's response tree will map from
 * these. {@link QuestionOptionResult} is the exception, in its own file because {@link QuestionBankItemResult} needs
 * the same shape too.
 */
public record ExamDetailResult(UUID id, String title, String description, ExamType examType,
        CertificateType certificateType, CertificateVariant certificateVariant, TargetLevel targetLevel,
        int durationSeconds, BigDecimal maxRawScore, BigDecimal passScore, ExamStatus status, int versionNumber,
        UUID createdByUserId, Instant publishedAt, Instant createdAt, List<ExamSectionResult> sections) {

    public static ExamDetailResult of(Exam exam, List<ExamSectionResult> sections) {
        return new ExamDetailResult(exam.getId(), exam.getTitle(), exam.getDescription(), exam.getExamType(),
                exam.getCertificateType(), exam.getCertificateVariant(), exam.getTargetLevel(),
                exam.getDurationSeconds(), exam.getMaxRawScore(), exam.getPassScore(), exam.getStatus(),
                exam.getVersionNumber(), exam.getCreatedByUserId(), exam.getPublishedAt(), exam.getCreatedAt(),
                sections);
    }

    public record ExamSectionResult(UUID id, SectionType sectionType, int orderNo, BigDecimal maxRawScore,
            boolean scoredByCriteria, Integer timeLimitSeconds, List<SectionPartResult> parts) {

        public static ExamSectionResult of(ExamSection section, List<SectionPartResult> parts) {
            return new ExamSectionResult(section.getId(), section.getSectionType(), section.getOrderNo(),
                    section.getMaxRawScore(), section.isScoredByCriteria(), section.getTimeLimitSeconds(), parts);
        }
    }

    /** Carries the raw object keys; turning them into presigned URLs is the response layer's job. */
    public record SectionPartResult(UUID id, int orderNo, String title, String instruction, String content,
            String audioObjectKey, String imageObjectKey, List<QuestionSetResult> questionSets) {

        public static SectionPartResult of(SectionPart part, List<QuestionSetResult> questionSets) {
            return new SectionPartResult(part.getId(), part.getOrderNo(), part.getTitle(), part.getInstruction(),
                    part.getContent(), part.getAudioObjectKey(), part.getImageObjectKey(), questionSets);
        }
    }

    /**
     * Carries the raw object keys; turning them into presigned URLs is the response layer's job.
     * {@code sourceQuestionSetId} is surfaced here so an admin who loads a paper, edits it, and saves it back through
     * {@code PUT /content} does not silently drop the bank provenance the frontend never re-derives on its own.
     */
    public record QuestionSetResult(UUID id, String title, String instruction, int orderNo, String content,
            String audioObjectKey, String imageObjectKey, UUID sourceQuestionSetId, List<QuestionResult> questions) {

        public static QuestionSetResult of(com.englow3.exam.entity.QuestionSet questionSet,
                List<QuestionResult> questions) {
            return new QuestionSetResult(questionSet.getId(), questionSet.getTitle(), questionSet.getInstruction(),
                    questionSet.getOrderNo(), questionSet.getContent(), questionSet.getAudioObjectKey(),
                    questionSet.getImageObjectKey(), questionSet.getSourceQuestionSetId(), questions);
        }
    }

    /** {@code sourceQuestionId} is surfaced for the same round-trip reason as {@code QuestionSetResult}'s. */
    public record QuestionResult(UUID id, QuestionType questionType, String content, DifficultyLevel difficultyLevel,
            SkillType skillType, String questionCategory, int orderNo, BigDecimal maxRawScore, String explanation,
            UUID sourceQuestionId, List<QuestionOptionResult> options) {

        public static QuestionResult of(com.englow3.exam.entity.Question question, List<QuestionOptionResult> options) {
            return new QuestionResult(question.getId(), question.getQuestionType(), question.getContent(),
                    question.getDifficultyLevel(), question.getSkillType(), question.getQuestionCategory(),
                    question.getOrderNo(), question.getMaxRawScore(), question.getExplanation(),
                    question.getSourceQuestionId(), options);
        }
    }
}
