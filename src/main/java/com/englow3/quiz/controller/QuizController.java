package com.englow3.quiz.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.quiz.dto.response.QuizAttemptResponse;
import com.englow3.quiz.dto.response.QuizSummaryResponse;
import com.englow3.quiz.dto.result.QuizAttemptResult;
import com.englow3.quiz.service.QuizService;
import com.englow3.shared.page.PageResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
class QuizController {

    private final QuizService quizService;

    @GetMapping
    ResponseEntity<PageResponse<QuizSummaryResponse>> search(@RequestParam(required = false) String category,
            @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse
                .from(quizService.searchPublished(category, title, pageable).map(QuizSummaryResponse::from)));
    }

    /** 201 for a new attempt, 200 for one that was already open - same convention as an exam. */
    @PostMapping("/{id}/attempts")
    ResponseEntity<QuizAttemptResponse> startAttempt(@PathVariable UUID id) {
        QuizAttemptResult result = quizService.start(id);
        return ResponseEntity.status(result.resumed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(QuizAttemptResponse.from(result));
    }
}
