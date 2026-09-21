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

public record LearnerExamPaperResponse(UUID id, String title, String description, ExamType examType,
        CertificateType certificateType, CertificateVariant certificateVariant, TargetLevel targetLevel,
        int durationSeconds, BigDecimal maxRawScore, BigDecimal passScore, int versionNumber,
        List<ExamSectionResponse> sections) {

    public static LearnerExamPaperResponse from(LearnerExamPaperResult result, ExamMediaUrls media) {
        return new LearnerExamPaperResponse(result.id(), result.title(), result.description(), result.examType(),
                result.certificateType(), result.certificateVariant(), result.targetLevel(), result.durationSeconds(),
                result.maxRawScore(), result.passScore(), result.versionNumber(),
                result.sections().stream().map(section -> ExamSectionResponse.from(section, media)).toList());
    }

    public record ExamSectionResponse(UUID id, SectionType sectionType, int orderNo, BigDecimal maxRawScore,
            boolean scoredByCriteria, Integer timeLimitSeconds, List<SectionPartResponse> parts) {
        static ExamSectionResponse from(LearnerExamPaperResult.ExamSectionResult result, ExamMediaUrls media) {
            return new ExamSectionResponse(result.id(), result.sectionType(), result.orderNo(), result.maxRawScore(),
                    result.scoredByCriteria(), result.timeLimitSeconds(),
                    result.parts().stream().map(part -> SectionPartResponse.from(part, media)).toList());
        }
    }

    public record SectionPartResponse(UUID id, int orderNo, String title, String instruction, String content,
            String audioUrl, String imageUrl, List<QuestionSetResponse> questionSets) {
        static SectionPartResponse from(LearnerExamPaperResult.SectionPartResult result, ExamMediaUrls media) {
            return new SectionPartResponse(result.id(), result.orderNo(), result.title(), result.instruction(),
                    result.content(), media.urlFor(result.audioObjectKey()), media.urlFor(result.imageObjectKey()),
                    result.questionSets().stream().map(set -> QuestionSetResponse.from(set, media)).toList());
        }
    }

    public record QuestionSetResponse(UUID id, String title, String instruction, int orderNo, String content,
            String audioUrl, String imageUrl, List<QuestionResponse> questions) {
        static QuestionSetResponse from(LearnerExamPaperResult.QuestionSetResult result, ExamMediaUrls media) {
            return new QuestionSetResponse(result.id(), result.title(), result.instruction(), result.orderNo(),
                    result.content(), media.urlFor(result.audioObjectKey()), media.urlFor(result.imageObjectKey()),
                    result.questions().stream().map(QuestionResponse::from).toList());
        }
    }

    public record QuestionResponse(UUID id, QuestionType questionType, String content, DifficultyLevel difficultyLevel,
            SkillType skillType, String questionCategory, int orderNo, BigDecimal maxRawScore,
            List<QuestionOptionResponse> options) {
        static QuestionResponse from(LearnerExamPaperResult.QuestionResult result) {
            return new QuestionResponse(result.id(), result.questionType(), result.content(), result.difficultyLevel(),
                    result.skillType(), result.questionCategory(), result.orderNo(), result.maxRawScore(),
                    result.options().stream().map(QuestionOptionResponse::from).toList());
        }
    }

    public record QuestionOptionResponse(UUID id, String content, int orderNo) {
        static QuestionOptionResponse from(LearnerExamPaperResult.QuestionOptionResult result) {
            return new QuestionOptionResponse(result.id(), result.content(), result.orderNo());
        }
    }
}
