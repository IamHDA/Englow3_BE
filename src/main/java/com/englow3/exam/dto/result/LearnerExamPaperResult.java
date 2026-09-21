package com.englow3.exam.dto.result;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.Exam;
import com.englow3.exam.entity.ExamSection;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.SectionPart;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.entity.SkillType;
import com.englow3.exam.entity.TargetLevel;

/** A paper safe to deliver before submission: it deliberately has no answer key or explanation fields. */
public record LearnerExamPaperResult(UUID id, String title, String description, ExamType examType,
        CertificateType certificateType, CertificateVariant certificateVariant, TargetLevel targetLevel,
        int durationSeconds, BigDecimal maxRawScore, BigDecimal passScore, int versionNumber,
        List<ExamSectionResult> sections) {

    public static LearnerExamPaperResult of(Exam exam, List<ExamSectionResult> sections) {
        return new LearnerExamPaperResult(exam.getId(), exam.getTitle(), exam.getDescription(), exam.getExamType(),
                exam.getCertificateType(), exam.getCertificateVariant(), exam.getTargetLevel(),
                exam.getDurationSeconds(), exam.getMaxRawScore(), exam.getPassScore(), exam.getVersionNumber(),
                sections);
    }

    public record ExamSectionResult(UUID id, SectionType sectionType, int orderNo, BigDecimal maxRawScore,
            boolean scoredByCriteria, Integer timeLimitSeconds, List<SectionPartResult> parts) {

        public static ExamSectionResult of(ExamSection section, List<SectionPartResult> parts) {
            return new ExamSectionResult(section.getId(), section.getSectionType(), section.getOrderNo(),
                    section.getMaxRawScore(), section.isScoredByCriteria(), section.getTimeLimitSeconds(), parts);
        }
    }

    public record SectionPartResult(UUID id, int orderNo, String title, String instruction, String content,
            String audioObjectKey, String imageObjectKey, List<QuestionSetResult> questionSets) {

        public static SectionPartResult of(SectionPart part, List<QuestionSetResult> questionSets) {
            return new SectionPartResult(part.getId(), part.getOrderNo(), part.getTitle(), part.getInstruction(),
                    part.getContent(), part.getAudioObjectKey(), part.getImageObjectKey(), questionSets);
        }
    }

    public record QuestionSetResult(UUID id, String title, String instruction, int orderNo, String content,
            String audioObjectKey, String imageObjectKey, List<QuestionResult> questions) {

        public static QuestionSetResult of(com.englow3.exam.entity.QuestionSet set, List<QuestionResult> questions) {
            return new QuestionSetResult(set.getId(), set.getTitle(), set.getInstruction(), set.getOrderNo(),
                    set.getContent(), set.getAudioObjectKey(), set.getImageObjectKey(), questions);
        }
    }

    public record QuestionResult(UUID id, QuestionType questionType, String content, DifficultyLevel difficultyLevel,
            SkillType skillType, String questionCategory, int orderNo, BigDecimal maxRawScore,
            List<QuestionOptionResult> options) {

        public static QuestionResult of(com.englow3.exam.entity.Question question, List<QuestionOptionResult> options) {
            return new QuestionResult(question.getId(), question.getQuestionType(), question.getContent(),
                    question.getDifficultyLevel(), question.getSkillType(), question.getQuestionCategory(),
                    question.getOrderNo(), question.getMaxRawScore(), options);
        }
    }

    public record QuestionOptionResult(UUID id, String content, int orderNo) {

        public static QuestionOptionResult of(com.englow3.exam.entity.QuestionOption option) {
            return new QuestionOptionResult(option.getId(), option.getContent(), option.getOrderNo());
        }
    }
}
