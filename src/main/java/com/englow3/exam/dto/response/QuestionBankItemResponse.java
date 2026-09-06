package com.englow3.exam.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.englow3.exam.dto.result.QuestionBankItemResult;
import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.QuestionType;
import com.englow3.exam.entity.SkillType;

/** Reuses {@link ExamDetailResponse.QuestionOptionResponse} - same shape, same reason as on the result side. */
public record QuestionBankItemResponse(UUID id, UUID questionSetId, QuestionType questionType, String content,
        DifficultyLevel difficultyLevel, SkillType skillType, String questionCategory, BigDecimal maxRawScore,
        String explanation, List<ExamDetailResponse.QuestionOptionResponse> options) {

    public static QuestionBankItemResponse from(QuestionBankItemResult result) {
        return new QuestionBankItemResponse(result.id(), result.questionSetId(), result.questionType(),
                result.content(), result.difficultyLevel(), result.skillType(), result.questionCategory(),
                result.maxRawScore(), result.explanation(),
                result.options().stream().map(ExamDetailResponse.QuestionOptionResponse::from).toList());
    }
}
