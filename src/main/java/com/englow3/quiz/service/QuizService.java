package com.englow3.quiz.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.englow3.quiz.dto.result.QuizSummaryResult;

public interface QuizService {
    Page<QuizSummaryResult> searchPublished(String category, String title, Pageable pageable);
}
