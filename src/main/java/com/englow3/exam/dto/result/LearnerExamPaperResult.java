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
        List<LearnerSection> sections) {

    public static LearnerExamPaperResult of(Exam exam, List<LearnerSection> sections) {
        return new LearnerExamPaperResult(exam.getId(), exam.getTitle(), exam.getDescription(), exam.getExamType(),
                exam.getCertificateType(), exam.getCertificateVariant(), exam.getTargetLevel(),
                exam.getDurationSeconds(), exam.getMaxRawScore(), exam.getPassScore(), exam.getVersionNumber(),
                sections);
    }

    public record LearnerSection(UUID id, SectionType sectionType, int orderNo, BigDecimal maxRawScore,
            boolean scoredByCriteria, Integer timeLimitSeconds, List<LearnerPart> parts) {

        public static LearnerSection of(ExamSection section, List<LearnerPart> parts) {
            return new LearnerSection(section.getId(), section.getSectionType(), section.getOrderNo(),
                    section.getMaxRawScore(), section.isScoredByCriteria(), section.getTimeLimitSeconds(), parts);
        }
    }

    public record LearnerPart(UUID id, int orderNo, String title, String instruction, String content, String audioUrl,
            String imageUrl, List<QuestionSetResult> questionSets) {

        public static LearnerPart of(SectionPart part, String audioUrl, String imageUrl,
                List<QuestionSetResult> questionSets) {
            return new LearnerPart(part.getId(), part.getOrderNo(), part.getTitle(), part.getInstruction(),
                    part.getContent(), audioUrl, imageUrl, questionSets);
        }
    }

    public record QuestionSetResult(UUID id, String title, String instruction, int orderNo, String content,
            String audioUrl, String imageUrl, List<QuestionResult> questions) {

        public static QuestionSetResult of(com.englow3.exam.entity.QuestionSet set, String audioUrl, String imageUrl,
                List<QuestionResult> questions) {
            return new QuestionSetResult(set.getId(), set.getTitle(), set.getInstruction(), set.getOrderNo(),
                    set.getContent(), audioUrl, imageUrl, questions);
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
