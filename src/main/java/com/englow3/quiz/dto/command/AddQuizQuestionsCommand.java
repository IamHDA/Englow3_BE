package com.englow3.quiz.dto.command;

import java.util.List;
import java.util.UUID;

import com.englow3.quiz.entity.QuizQuestionType;

public record AddQuizQuestionsCommand(UUID quizId, List<NewQuestion> questions) {

    /**
     * One question with whichever payload its type needs. The unused lists are empty rather than null so the service
     * never has to ask which shape it is holding before reading them.
     */
    public record NewQuestion(QuizQuestionType questionType, String title, String prompt, short points,
            String explanation, String beforeText, String afterText, String originalSentence, String rewriteKeyword,
            List<NewOption> options, List<String> acceptedAnswers, List<String> wordBank, List<String> correctWords,
            List<String> scrambledWords, List<String> correctOrder, List<NewPair> pairs) {
    }

    public record NewOption(String label, String content, boolean correct) {
    }

    public record NewPair(String leftText, String rightText) {
    }
}
