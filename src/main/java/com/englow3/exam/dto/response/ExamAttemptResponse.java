package com.englow3.exam.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.dto.result.ExamAttemptResult;
import com.englow3.exam.entity.ExamAttemptMode;
import com.englow3.exam.entity.ExamAttemptStatus;
import com.englow3.exam.entity.SectionType;
import io.swagger.v3.oas.annotations.media.Schema;

public record ExamAttemptResponse(UUID id, UUID examId, ExamAttemptStatus status, Instant startedAt, Instant expiresAt,
        @Schema(nullable = true) Instant submittedAt, @Schema(nullable = true) Instant scoredAt,
        @Schema(nullable = true) BigDecimal rawScore, BigDecimal maxRawScore,
        @Schema(nullable = true) BigDecimal scorePercentage, @Schema(nullable = true) Integer correctAnswerCount,
        int questionCount, boolean resumed, @Schema(nullable = true) String examTitle,
        List<QuestionReviewResponse> questions, ExamAttemptMode mode,
        @Schema(nullable = true, description = "Null only for an untimed practice") Integer timeLimitSeconds,
        @Schema(description = "Parts a practice covers; empty for a full attempt") List<AttemptPartResponse> parts,
        @Schema(description = "Each section's points and band; empty until scored") List<SectionScoreResponse> sections) {

    public static ExamAttemptResponse from(ExamAttemptResult result) {
        return new ExamAttemptResponse(result.id(), result.examId(), result.status(), result.startedAt(),
                result.expiresAt(), result.submittedAt(), result.scoredAt(), result.rawScore(), result.maxRawScore(),
                result.scorePercentage(), result.correctAnswerCount(), result.questionCount(), result.resumed(),
                result.examTitle(), result.questions().stream().map(QuestionReviewResponse::from).toList(),
                result.mode(), result.timeLimitSeconds(),
                result.parts().stream().map(AttemptPartResponse::from).toList(),
                result.sections().stream().map(SectionScoreResponse::from).toList());
    }

    public record SectionScoreResponse(SectionType sectionType, @Schema(nullable = true) BigDecimal rawScore,
            BigDecimal maxRawScore,
            @Schema(nullable = true, description = "Estimated band; null where the certificate has no conversion for this section") BigDecimal band,
            @Schema(nullable = true) Integer correctAnswerCount, int questionCount) {
        static SectionScoreResponse from(ExamAttemptResult.SectionScore score) {
            return new SectionScoreResponse(score.sectionType(), score.rawScore(), score.maxRawScore(), score.band(),
                    score.correctAnswerCount(), score.questionCount());
        }
    }

    public record AttemptPartResponse(UUID id, SectionType sectionType, String title) {
        static AttemptPartResponse from(ExamAttemptResult.AttemptPart part) {
            return new AttemptPartResponse(part.id(), part.sectionType(), part.title());
        }
    }

    public record QuestionReviewResponse(UUID questionId, List<UUID> selectedOptionIds, List<UUID> correctOptionIds,
            boolean correct, BigDecimal awardedRawScore, @Schema(nullable = true) String explanation,
            List<OptionReviewResponse> options, @Schema(nullable = true) String textAnswer,
            List<String> acceptedAnswers, int numberFrom, int numberTo) {

        static QuestionReviewResponse from(ExamAttemptResult.QuestionReviewResult result) {
            return new QuestionReviewResponse(result.questionId(), result.selectedOptionIds(),
                    result.correctOptionIds(), result.correct(), result.awardedRawScore(), result.explanation(),
                    result.options().stream().map(OptionReviewResponse::from).toList(), result.textAnswer(),
                    result.acceptedAnswers(), result.numberFrom(), result.numberTo());
        }
    }

    public record OptionReviewResponse(UUID optionId, boolean correct, @Schema(nullable = true) String explanation) {
        static OptionReviewResponse from(ExamAttemptResult.OptionReviewResult result) {
            return new OptionReviewResponse(result.optionId(), result.correct(), result.explanation());
        }
    }
}
