package com.englow3.learning.dto.request;

import java.util.List;

import com.englow3.learning.entity.QuizQuestionType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Which lists a question needs depends on its type, so they are all optional here and checked in the service, where the
 * type is known. Bean validation cannot express "required when questionType is REORDER" without a custom validator that
 * would say the same thing twice.
 */
public record AddQuizQuestionsRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid QuestionRequest> questions) {

    public record QuestionRequest(@NotNull QuizQuestionType questionType, @NotBlank @Size(max = 200) String title,
            @NotBlank String prompt, @Positive short points, @Size(max = 2000) String explanation, String beforeText,
            String afterText, String originalSentence, @Size(max = 100) String rewriteKeyword,
            @Size(max = 10) List<@NotNull @Valid OptionRequest> options,
            @Size(max = 10) List<@NotNull String> acceptedAnswers, @Size(max = 30) List<@NotNull String> wordBank,
            @Size(max = 30) List<@NotNull String> correctWords, @Size(max = 30) List<@NotNull String> scrambledWords,
            @Size(max = 30) List<@NotNull String> correctOrder,
            @Size(max = 10) List<@NotNull @Valid PairRequest> pairs) {
    }

    public record OptionRequest(@NotBlank @Size(max = 4) String label, @NotBlank String content, boolean correct) {
    }

    public record PairRequest(@NotBlank String leftText, @NotBlank String rightText) {
    }
}
