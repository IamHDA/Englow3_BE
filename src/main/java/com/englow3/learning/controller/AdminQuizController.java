package com.englow3.learning.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.learning.entity.QuizStatus;
import com.englow3.learning.dto.command.AddQuizQuestionsCommand;
import com.englow3.learning.dto.command.AddQuizQuestionsCommand.NewOption;
import com.englow3.learning.dto.command.AddQuizQuestionsCommand.NewPair;
import com.englow3.learning.dto.command.AddQuizQuestionsCommand.NewQuestion;
import com.englow3.learning.dto.command.CreateQuizCommand;
import com.englow3.learning.dto.request.AddQuizQuestionsRequest;
import com.englow3.learning.dto.request.CreateQuizRequest;
import com.englow3.learning.dto.request.RejectContentRequest;
import com.englow3.learning.dto.response.ContentReviewResponse;
import com.englow3.learning.dto.response.QuizSummaryResponse;
import com.englow3.shared.page.PageResponse;
import com.englow3.learning.service.AdminQuizService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/quizzes")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
class AdminQuizController {

    private final AdminQuizService adminQuizService;

    @PostMapping
    ResponseEntity<QuizSummaryResponse> create(@Valid @RequestBody CreateQuizRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(QuizSummaryResponse.from(adminQuizService.create(new CreateQuizCommand(request.slug(),
                        request.title(), request.description(), request.category(), request.targetLevel(),
                        request.timeLimitSeconds(), request.passingScorePercent()))));
    }

    @PostMapping("/{id}/questions")
    ResponseEntity<QuizSummaryResponse> addQuestions(@PathVariable UUID id,
            @Valid @RequestBody AddQuizQuestionsRequest request) {
        var questions = request.questions().stream().map(question -> new NewQuestion(question.questionType(),
                question.title(), question.prompt(), question.points(), question.explanation(), question.beforeText(),
                question.afterText(), question.originalSentence(), question.rewriteKeyword(),
                question.options() == null ? null
                        : question.options().stream()
                                .map(option -> new NewOption(option.label(), option.content(), option.correct()))
                                .toList(),
                question.acceptedAnswers(), question.wordBank(), question.correctWords(), question.scrambledWords(),
                question.correctOrder(),
                question.pairs() == null ? null
                        : question.pairs().stream().map(pair -> new NewPair(pair.leftText(), pair.rightText()))
                                .toList()))
                .toList();

        return ResponseEntity.status(HttpStatus.CREATED).body(
                QuizSummaryResponse.from(adminQuizService.addQuestions(new AddQuizQuestionsCommand(id, questions))));
    }

    /** Staff hand a quiz over for review. Available from a draft or from one that came back. */
    @PostMapping("/{id}/submit-for-review")
    ResponseEntity<ContentReviewResponse> submitForReview(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminQuizService.submitForReview(id)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminQuizService.approve(id)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> reject(@PathVariable UUID id,
            @Valid @RequestBody RejectContentRequest request) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminQuizService.reject(id, request.note())));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminQuizService.publish(id)));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminQuizService.archive(id)));
    }
}
