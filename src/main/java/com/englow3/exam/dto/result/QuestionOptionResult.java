package com.englow3.exam.dto.result;

import java.util.UUID;

import com.englow3.exam.entity.QuestionOption;

/** Shared by {@link ExamDetailResult} and {@link QuestionBankItemResult} - both need the same answer-key shape. */
public record QuestionOptionResult(UUID id, String content, int orderNo, boolean correct, String explanation) {

    public static QuestionOptionResult of(QuestionOption option) {
        return new QuestionOptionResult(option.getId(), option.getContent(), option.getOrderNo(), option.isCorrect(),
                option.getExplanation());
    }
}
