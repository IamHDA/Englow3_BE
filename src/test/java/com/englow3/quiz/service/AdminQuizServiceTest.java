package com.englow3.quiz.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.quiz.entity.Quiz;
import com.englow3.quiz.repository.QuizQuestionRepository;
import com.englow3.quiz.repository.QuizRepository;
import com.englow3.shared.error.ConflictException;
import com.englow3.user.api.UserDirectory;

class AdminQuizServiceTest {

    private final QuizRepository quizRepo = mock(QuizRepository.class);
    private final QuizQuestionRepository questionRepo = mock(QuizQuestionRepository.class);
    private final AdminQuizService service = new com.englow3.quiz.service.impl.AdminQuizServiceImpl(quizRepo,
            questionRepo, mock(UserDirectory.class));

    @Test
    void refusesToPublishAQuizWhoseQuestionsAreAllWorthNothing() {
        Quiz quiz = Quiz.draft("present-perfect", "Present perfect", "", "Grammar", "B1", 600, (short) 60,
                UUID.randomUUID());
        when(quizRepo.findById(quiz.getId())).thenReturn(Optional.of(quiz));
        when(questionRepo.countByQuizId(quiz.getId())).thenReturn(3L);
        when(questionRepo.sumPoints(quiz.getId())).thenReturn(0L);

        assertThatThrownBy(() -> service.publish(quiz.getId())).isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("code", "QUIZ_ZERO_POINTS");
    }
}
