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
import com.englow3.exam.entity.Question;
import com.englow3.exam.entity.QuestionOption;
import com.englow3.exam.entity.QuestionSet;
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

    /**
     * @param setOptions
     *            the list the set's MATCHING questions choose from; empty when it has none
     */
    public record QuestionSetResult(UUID id, String title, String instruction, int orderNo, String content,
            String audioUrl, String imageUrl, List<QuestionResult> questions, List<SetOptionResult> setOptions) {
    }

    public record SetOptionResult(UUID id, String label, String content, int orderNo) {
    }

    /**
     * @param numberFrom
     *            the number the learner sees, computed over the whole paper; {@code numberTo} differs only on a "choose
     *            N" question, which takes N numbers
     * @param choiceLimit
     *            how many options may be chosen; 0 on a TEXT_ENTRY question
     */
    public record QuestionResult(UUID id, QuestionType questionType, String content, DifficultyLevel difficultyLevel,
            SkillType skillType, String questionCategory, int orderNo, BigDecimal maxRawScore,
            List<QuestionOptionResult> options, int numberFrom, int numberTo, int choiceLimit) {
    }

    public record QuestionOptionResult(UUID id, String content, int orderNo) {

        public static QuestionOptionResult of(QuestionOption option) {
            return new QuestionOptionResult(option.getId(), option.getContent(), option.getOrderNo());
        }
    }
}
