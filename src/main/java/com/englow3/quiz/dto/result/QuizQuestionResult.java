package com.englow3.quiz.dto.result;

import java.util.List;
import java.util.UUID;

import com.englow3.quiz.entity.QuizQuestionType;

/**
 * A question as the learner sees it while sitting. Everything that would give the answer away is absent rather than
 * blanked: no correct flag on an option, no accepted answers, no correct order. They arrive afterwards on the review.
 * {@code rightTexts} for MATCHING is shuffled before it leaves the service - sending the pairs in their stored order
 * would hand over the pairing itself.
 */
public record QuizQuestionResult(UUID id, int orderNo, QuizQuestionType questionType, String title, String prompt,
        int points, String beforeText, String afterText, String originalSentence, String rewriteKeyword,
        List<OptionResult> options, List<String> wordBank, List<String> scrambledWords, List<String> leftTexts,
        List<String> rightTexts) {

    public record OptionResult(UUID id, int orderNo, String label, String content) {
    }
}
