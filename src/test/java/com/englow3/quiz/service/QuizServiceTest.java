package com.englow3.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.englow3.quiz.entity.QuizCategory;
import com.englow3.quiz.entity.QuizStatus;
import com.englow3.quiz.repository.QuizQuestionRepository;
import com.englow3.quiz.repository.QuizRepository;
import com.englow3.quiz.service.impl.QuizServiceImpl;

class QuizServiceTest {

    private final QuizRepository quizRepo = mock(QuizRepository.class);
    private final QuizQuestionRepository questionRepo = mock(QuizQuestionRepository.class);
    private final QuizService service = new QuizServiceImpl(quizRepo, questionRepo);
    private final PageRequest pageable = PageRequest.of(0, 10);

    @Test
    void findsNothingForACategoryThatDoesNotExist() {
        Page<?> page = service.searchPublished("Grammar", null, pageable);

        assertThat(page.getContent()).isEmpty();
        verifyNoInteractions(quizRepo);
    }

    @Test
    void filtersByTheCategoryItIsGiven() {
        when(quizRepo.searchByStatus(QuizStatus.PUBLISHED, QuizCategory.VOCABULARY, null, pageable))
                .thenReturn(Page.empty(pageable));

        service.searchPublished("VOCABULARY", null, pageable);

        verify(quizRepo).searchByStatus(QuizStatus.PUBLISHED, QuizCategory.VOCABULARY, null, pageable);
    }

    @Test
    void searchesEveryCategoryWhenNoneIsGiven() {
        when(quizRepo.searchByStatus(QuizStatus.PUBLISHED, null, null, pageable)).thenReturn(Page.empty(pageable));

        service.searchPublished(null, null, pageable);

        verify(quizRepo).searchByStatus(QuizStatus.PUBLISHED, null, null, pageable);
    }
}
