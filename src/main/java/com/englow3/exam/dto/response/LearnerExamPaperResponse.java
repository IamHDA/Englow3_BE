package com.englow3.exam.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.dto.result.LearnerExamPaperResult;
import com.englow3.exam.entity.CertificateType;
import com.englow3.exam.entity.CertificateVariant;
import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.ExamType;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.SectionType;
import com.englow3.exam.entity.SkillType;
import com.englow3.exam.entity.TargetLevel;
import io.swagger.v3.oas.annotations.media.Schema;

public record LearnerExamPaperResponse(UUID id, String title, String description, ExamType examType,
        @Schema(nullable = true) CertificateType certificateType,
        @Schema(nullable = true) CertificateVariant certificateVariant,
        @Schema(nullable = true) TargetLevel targetLevel, int durationSeconds, BigDecimal maxRawScore,
        @Schema(nullable = true) BigDecimal passScore, int versionNumber, List<ExamSectionResponse> sections) {

    public static LearnerExamPaperResponse from(LearnerExamPaperResult result) {
        return new LearnerExamPaperResponse(result.id(), result.title(), result.description(), result.examType(),
                result.certificateType(), result.certificateVariant(), result.targetLevel(), result.durationSeconds(),
                result.maxRawScore(), result.passScore(), result.versionNumber(),
                result.sections().stream().map(ExamSectionResponse::from).toList());
    }

    public record ExamSectionResponse(UUID id, SectionType sectionType, int orderNo, BigDecimal maxRawScore,
            boolean scoredByCriteria, @Schema(nullable = true) Integer timeLimitSeconds,
            List<SectionPartResponse> parts) {
        static ExamSectionResponse from(LearnerExamPaperResult.LearnerSection result) {
            return new ExamSectionResponse(result.id(), result.sectionType(), result.orderNo(), result.maxRawScore(),
                    result.scoredByCriteria(), result.timeLimitSeconds(),
                    result.parts().stream().map(SectionPartResponse::from).toList());
        }
    }

    public record SectionPartResponse(UUID id, int orderNo, String title, @Schema(nullable = true) String instruction,
            @Schema(nullable = true) String content, @Schema(nullable = true) String audioUrl,
            @Schema(nullable = true) String imageUrl, List<QuestionSetResponse> questionSets) {
        static SectionPartResponse from(LearnerExamPaperResult.LearnerPart result) {
            return new SectionPartResponse(result.id(), result.orderNo(), result.title(), result.instruction(),
                    result.content(), result.audioUrl(), result.imageUrl(),
                    result.questionSets().stream().map(QuestionSetResponse::from).toList());
        }
    }

    public record QuestionSetResponse(UUID id, @Schema(nullable = true) String title,
            @Schema(nullable = true) String instruction, int orderNo, @Schema(nullable = true) String content,
            @Schema(nullable = true) String audioUrl, @Schema(nullable = true) String imageUrl,
            List<QuestionResponse> questions,
            @Schema(description = "The list this set's MATCHING questions choose from; empty when it has none") List<SetOptionResponse> setOptions) {
        static QuestionSetResponse from(LearnerExamPaperResult.QuestionSetResult result) {
            return new QuestionSetResponse(result.id(), result.title(), result.instruction(), result.orderNo(),
                    result.content(), result.audioUrl(), result.imageUrl(),
                    result.questions().stream().map(QuestionResponse::from).toList(),
                    result.setOptions().stream().map(SetOptionResponse::from).toList());
        }
    }

    public record SetOptionResponse(UUID id, String label, @Schema(nullable = true) String content, int orderNo) {
        static SetOptionResponse from(LearnerExamPaperResult.SetOptionResult result) {
            return new SetOptionResponse(result.id(), result.label(), result.content(), result.orderNo());
        }
    }

    public record QuestionResponse(UUID id, QuestionType questionType, String content, DifficultyLevel difficultyLevel,
            SkillType skillType, @Schema(nullable = true) String questionCategory, int orderNo, BigDecimal maxRawScore,
            List<QuestionOptionResponse> options,
            @Schema(description = "The number shown to the learner, computed over the whole paper") int numberFrom,
            @Schema(description = "Equals numberFrom except on a choose-N question, which takes N numbers") int numberTo,
            @Schema(description = "How many options may be chosen; 0 on a TEXT_ENTRY question") int choiceLimit) {
        static QuestionResponse from(LearnerExamPaperResult.QuestionResult result) {
            return new QuestionResponse(result.id(), result.questionType(), result.content(), result.difficultyLevel(),
                    result.skillType(), result.questionCategory(), result.orderNo(), result.maxRawScore(),
                    result.options().stream().map(QuestionOptionResponse::from).toList(), result.numberFrom(),
                    result.numberTo(), result.choiceLimit());
        }
    }

    public record QuestionOptionResponse(UUID id, String content, int orderNo) {
        static QuestionOptionResponse from(LearnerExamPaperResult.QuestionOptionResult result) {
            return new QuestionOptionResponse(result.id(), result.content(), result.orderNo());
        }
    }
}
