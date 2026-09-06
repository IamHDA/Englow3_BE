package com.englow3.exam.dto.response;

import java.util.UUID;

import com.englow3.exam.dto.result.QuestionOptionResult;

/** Shared by {@link ExamDetailResponse} and {@link QuestionBankItemResponse} - both need the same answer-key shape. */
public record QuestionOptionResponse(UUID id, String content, int orderNo, boolean correct, String explanation) {

    public static QuestionOptionResponse from(QuestionOptionResult result) {
        return new QuestionOptionResponse(result.id(), result.content(), result.orderNo(), result.correct(),
                result.explanation());
    }
}
