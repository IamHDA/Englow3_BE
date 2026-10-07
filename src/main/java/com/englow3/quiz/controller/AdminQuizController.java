package com.englow3.quiz.controller;

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

import com.englow3.quiz.entity.QuizStatus;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewOption;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewPair;
import com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewQuestion;
import com.englow3.quiz.dto.command.CreateQuizCommand;
import com.englow3.quiz.dto.request.AddQuizQuestionsRequest;
import com.englow3.quiz.dto.request.CreateQuizRequest;
import com.englow3.quiz.dto.request.RejectContentRequest;
import com.englow3.quiz.dto.response.ContentReviewResponse;
import com.englow3.quiz.dto.response.QuizSummaryResponse;
import com.englow3.shared.page.PageResponse;
import com.englow3.quiz.service.AdminQuizService;
import com.englow3.quiz.service.QuizQuestionAuthoringService;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/quizzes")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
class AdminQuizController {

    private final AdminQuizService adminQuizService;
    private final QuizQuestionAuthoringService quizQuestionAuthoringService;

    /**
     * The authoring list. Separate from the learner catalogue because that one shows published quizzes only - an
     * administrator with no way to see a draft has no way to review one. A null status means every status, so the same
     * endpoint serves the full list and the review queue.
     */
    @GetMapping
    ResponseEntity<PageResponse<ContentReviewResponse>> search(@RequestParam(required = false) QuizStatus status,
            @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse
                .from(adminQuizService.searchForAuthoring(status, title, pageable).map(ContentReviewResponse::from)));
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Quiz created")
    ResponseEntity<QuizSummaryResponse> create(@Valid @RequestBody CreateQuizRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(QuizSummaryResponse.from(adminQuizService.create(new CreateQuizCommand(request.slug(),
                        request.title(), request.description(), request.category(), request.targetLevel(),
                        request.timeLimitSeconds(), request.passingScorePercent()))));
    }

    @PostMapping("/{id}/questions")
    @ApiResponse(responseCode = "201", description = "Quiz questions added")
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

        return ResponseEntity.status(HttpStatus.CREATED).body(QuizSummaryResponse
                .from(quizQuestionAuthoringService.addQuestions(new AddQuizQuestionsCommand(id, questions))));
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

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminQuizService.restore(id)));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminQuizService.archive(id)));
    }

    @GetMapping("/{id}/authoring")
    ResponseEntity<com.englow3.quiz.dto.response.AuthoringResponse> authoring(@PathVariable UUID id) {
        return ResponseEntity
                .ok(com.englow3.quiz.dto.response.AuthoringResponse.from(adminQuizService.authoringDetail(id)));
    }

    @PostMapping("/authoring")
    ResponseEntity<com.englow3.quiz.dto.response.AuthoringResponse> createAuthoring(
            @Valid @RequestBody com.englow3.quiz.dto.request.SaveAuthoringRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(com.englow3.quiz.dto.response.AuthoringResponse
                .from(adminQuizService.saveAuthoring(authoringCommand(null, request))));
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}/authoring")
    ResponseEntity<com.englow3.quiz.dto.response.AuthoringResponse> updateAuthoring(@PathVariable UUID id,
            @Valid @RequestBody com.englow3.quiz.dto.request.SaveAuthoringRequest request) {
        return ResponseEntity.ok(com.englow3.quiz.dto.response.AuthoringResponse
                .from(adminQuizService.saveAuthoring(authoringCommand(id, request))));
    }

    private com.englow3.quiz.dto.command.SaveAuthoringCommand authoringCommand(UUID id,
            com.englow3.quiz.dto.request.SaveAuthoringRequest r) {
        var m = r.metadata();
        return new com.englow3.quiz.dto.command.SaveAuthoringCommand(id, r.version(),
                new com.englow3.quiz.dto.command.CreateQuizCommand(m.slug(), m.title(), m.description(), m.category(),
                        m.targetLevel(), m.timeLimitSeconds(), m.passingScorePercent()),
                r.content().questions().stream()
                        .map(q -> new com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewQuestion(q.questionType(),
                                q.title(), q.prompt(), q.points(), q.explanation(), q.beforeText(), q.afterText(),
                                q.originalSentence(), q.rewriteKeyword(),
                                q.options() == null ? null
                                        : q.options().stream().map(
                                                o -> new com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewOption(
                                                        o.label(), o.content(), o.correct()))
                                                .toList(),
                                q.acceptedAnswers(), q.wordBank(), q.correctWords(), q.scrambledWords(),
                                q.correctOrder(),
                                q.pairs() == null ? null
                                        : q.pairs().stream().map(
                                                p -> new com.englow3.quiz.dto.command.AddQuizQuestionsCommand.NewPair(
                                                        p.leftText(), p.rightText()))
                                                .toList()))
                        .toList());
    }
}
