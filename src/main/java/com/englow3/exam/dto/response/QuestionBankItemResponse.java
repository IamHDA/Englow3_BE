package com.englow3.exam.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.dto.result.QuestionBankItemResult;
import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.SkillType;
import io.swagger.v3.oas.annotations.media.Schema;

/** Reuses {@link QuestionOptionResponse} - same shape, same reason as on the result side. */
public record QuestionBankItemResponse(UUID id, UUID questionSetId, QuestionType questionType, String content,
        DifficultyLevel difficultyLevel, SkillType skillType, @Schema(nullable = true) String questionCategory,
        BigDecimal maxRawScore, @Schema(nullable = true) String explanation, List<QuestionOptionResponse> options,
        @Schema(description = "TEXT_ENTRY: every accepted spelling; empty otherwise") List<AcceptedAnswerResponse> acceptedAnswers,
        @Schema(description = "MATCHING: labels of the set's list entries that answer it; empty otherwise") List<String> matchingLabels,
        @Schema(description = "MATCHING: the set's whole list, which a copy needs for its labels; empty otherwise") List<SetOptionResponse> setOptions) {

    public record AcceptedAnswerResponse(String text, boolean caseSensitive, int orderNo) {
    }

    public record SetOptionResponse(String label, @Schema(nullable = true) String content, int orderNo) {
    }

    public static QuestionBankItemResponse from(QuestionBankItemResult result) {
        return new QuestionBankItemResponse(result.id(), result.questionSetId(), result.questionType(),
                result.content(), result.difficultyLevel(), result.skillType(), result.questionCategory(),
                result.maxRawScore(), result.explanation(),
                result.options().stream().map(QuestionOptionResponse::from).toList(),
                result.acceptedAnswers().stream()
                        .map(a -> new AcceptedAnswerResponse(a.text(), a.caseSensitive(), a.orderNo())).toList(),
                result.matchingLabels(), result.setOptions().stream()
                        .map(o -> new SetOptionResponse(o.label(), o.content(), o.orderNo())).toList());
    }
}
