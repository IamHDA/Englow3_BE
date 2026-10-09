package com.englow3.quiz.service;

import java.util.List;
import java.util.UUID;

import com.englow3.quiz.dto.command.AddQuizQuestionsCommand;
import com.englow3.quiz.dto.result.QuizSummaryResult;

public interface QuizQuestionAuthoringService {
    List<AddQuizQuestionsCommand.NewQuestion> authoringQuestions(UUID id);

    QuizSummaryResult replaceQuestions(AddQuizQuestionsCommand command);

    QuizSummaryResult addQuestions(AddQuizQuestionsCommand command);
}
