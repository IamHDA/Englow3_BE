package com.englow3.learning.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.learning.dto.result.QuizPaperResult;
import com.englow3.learning.dto.result.QuizQuestionResult;
import com.englow3.learning.entity.QuizQuestionType;

public record QuizPaperResponse(UUID attemptId, UUID quizId, String title, String description, int timeLimitSeconds,
        Instant expiresAt, List<QuestionResponse> questions) {

    public static QuizPaperResponse from(QuizPaperResult result) {
        return new QuizPaperResponse(result.attemptId(), result.quizId(), result.title(), result.description(),
                result.timeLimitSeconds(), result.expiresAt(),
                result.questions().stream().map(QuestionResponse::from).toList());
    }

    public record QuestionResponse(UUID id, int orderNo, QuizQuestionType questionType, String title, String prompt,
            int points, String beforeText, String afterText, String originalSentence, String rewriteKeyword,
            List<OptionResponse> options, List<String> wordBank, List<String> scrambledWords, List<String> leftTexts,
            List<String> rightTexts) {

        static QuestionResponse from(QuizQuestionResult result) {
            return new QuestionResponse(result.id(), result.orderNo(), result.questionType(), result.title(),
                    result.prompt(), result.points(), result.beforeText(), result.afterText(),
                    result.originalSentence(), result.rewriteKeyword(),
                    result.options().stream().map(OptionResponse::from).toList(), result.wordBank(),
                    result.scrambledWords(), result.leftTexts(), result.rightTexts());
        }
    }

    public record OptionResponse(UUID id, int orderNo, String label, String content) {
        static OptionResponse from(QuizQuestionResult.OptionResult result) {
            return new OptionResponse(result.id(), result.orderNo(), result.label(), result.content());
        }
    }
}
