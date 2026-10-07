package com.englow3.quiz.service;

import com.englow3.quiz.dto.command.AddQuizQuestionsCommand;
import com.englow3.quiz.dto.result.QuizSummaryResult;

public interface QuizQuestionAuthoringService {
    java.util.List<com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewQuestion> authoringQuestions(
            java.util.UUID id);

    QuizSummaryResult replaceQuestions(AddQuizQuestionsCommand command);

    QuizSummaryResult addQuestions(AddQuizQuestionsCommand command);
}
