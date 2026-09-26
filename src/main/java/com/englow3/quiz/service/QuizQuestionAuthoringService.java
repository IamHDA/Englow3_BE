package com.englow3.quiz.service;

import com.englow3.quiz.dto.command.AddQuizQuestionsCommand;
import com.englow3.quiz.dto.result.QuizSummaryResult;

public interface QuizQuestionAuthoringService {
    QuizSummaryResult addQuestions(AddQuizQuestionsCommand command);
}
