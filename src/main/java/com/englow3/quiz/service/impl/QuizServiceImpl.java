package com.englow3.quiz.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.quiz.dto.result.QuizSummaryResult;
import com.englow3.quiz.entity.Quiz;
import com.englow3.quiz.entity.QuizStatus;
import com.englow3.quiz.repository.QuizQuestionRepository;
import com.englow3.quiz.repository.QuizRepository;
import com.englow3.quiz.service.QuizService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final QuizRepository quizRepo;
    private final QuizQuestionRepository questionRepo;

    @Transactional(readOnly = true)
    public Page<QuizSummaryResult> searchPublished(String category, String title, Pageable pageable) {
        Page<Quiz> page = quizRepo.searchByStatus(QuizStatus.PUBLISHED, category, title, pageable);
        return page.map(quiz -> QuizSummaryResult.of(quiz, questionRepo.countByQuizId(quiz.getId()), null, 0));
    }
}
