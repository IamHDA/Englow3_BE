package com.englow3.quiz.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.quiz.dto.request.SubmitQuizAttemptRequest;
import com.englow3.quiz.dto.response.QuizAttemptResponse;
import com.englow3.quiz.dto.response.QuizPaperResponse;
import com.englow3.quiz.service.QuizService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The paper is reachable only through an attempt, never by quiz id: the answer key is stripped per attempt, and the
 * matching columns are shuffled with a seed derived from it.
 */
@RestController
@RequestMapping("/api/quiz-attempts")
@RequiredArgsConstructor
class QuizAttemptController {

    private final QuizService quizService;

    @GetMapping("/{id}/paper")
    ResponseEntity<QuizPaperResponse> paper(@PathVariable UUID id) {
        return ResponseEntity.ok(QuizPaperResponse.from(quizService.paperForAttempt(id)));
    }

    @PostMapping("/{id}/submit")
    ResponseEntity<QuizAttemptResponse> submit(@PathVariable UUID id,
            @Valid @RequestBody SubmitQuizAttemptRequest request) {
        return ResponseEntity.ok(QuizAttemptResponse.from(quizService.submit(request.toCommand(id))));
    }

    @GetMapping("/{id}/result")
    ResponseEntity<QuizAttemptResponse> result(@PathVariable UUID id) {
        return ResponseEntity.ok(QuizAttemptResponse.from(quizService.result(id)));
    }
}
