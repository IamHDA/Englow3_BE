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
 * the same descent with those left out. The five levels below are nested rather than five files of their own: each is
 * the field type of the level above it and nothing else refers to them, so a file each would only scatter one shape
 * across six places. Nesting also lets the whole tree be read top to bottom here. They stay public - the sitting's
 * response tree will map from these.
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

    /** Carries the raw object keys; turning them into presigned URLs is the response layer's job. */
    public record QuestionSetResult(UUID id, String title, String instruction, int orderNo, String content,
            String audioObjectKey, String imageObjectKey, List<QuestionResult> questions) {

        public static QuestionSetResult of(com.englow3.exam.entity.QuestionSet questionSet,
                List<QuestionResult> questions) {
            return new QuestionSetResult(questionSet.getId(), questionSet.getTitle(), questionSet.getInstruction(),
                    questionSet.getOrderNo(), questionSet.getContent(), questionSet.getAudioObjectKey(),
                    questionSet.getImageObjectKey(), questions);
        }
    }

    public record QuestionResult(UUID id, QuestionType questionType, String content, DifficultyLevel difficultyLevel,
            SkillType skillType, String questionCategory, int orderNo, BigDecimal maxRawScore, String explanation,
            List<QuestionOptionResult> options) {

        public static QuestionResult of(com.englow3.exam.entity.Question question, List<QuestionOptionResult> options) {
            return new QuestionResult(question.getId(), question.getQuestionType(), question.getContent(),
                    question.getDifficultyLevel(), question.getSkillType(), question.getQuestionCategory(),
                    question.getOrderNo(), question.getMaxRawScore(), question.getExplanation(), options);
        }
    }

    public record QuestionOptionResult(UUID id, String content, int orderNo, boolean correct, String explanation) {

        public static QuestionOptionResult of(com.englow3.exam.entity.QuestionOption option) {
            return new QuestionOptionResult(option.getId(), option.getContent(), option.getOrderNo(),
                    option.isCorrect(), option.getExplanation());
        }
    }
}
