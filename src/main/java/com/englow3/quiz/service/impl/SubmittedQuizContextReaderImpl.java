package com.englow3.quiz.service.impl;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englow3.quiz.api.SubmittedQuizContextReader;
import com.englow3.quiz.service.QuizAttemptService;
import com.englow3.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubmittedQuizContextReaderImpl implements SubmittedQuizContextReader {
    private final QuizAttemptService attempts;

    @Override
    @Transactional(readOnly = true)
    public Question read(UUID attemptId, UUID questionId) {
        var result = attempts.result(attemptId);
        var question = result.reviews().stream().filter(row -> row.questionId().equals(questionId)).findFirst()
                .orElseThrow(() -> new NotFoundException("QUIZ_QUESTION_NOT_FOUND",
                        "Question not found in this submitted quiz"));
        return new Question(attemptId, questionId, limit(result.quizTitle(), 200), limit(question.prompt(), 1000),
                limit(question.userAnswerText(), 600), limit(question.correctAnswerText(), 600),
                limit(question.explanation(), 1000), question.correct());
    }

    private static String limit(String text, int max) {
        if (text == null)
            return "";
        int count = text.codePointCount(0, text.length());
        return count <= max ? text : text.substring(0, text.offsetByCodePoints(0, max)) + " [truncated]";
    }
}
