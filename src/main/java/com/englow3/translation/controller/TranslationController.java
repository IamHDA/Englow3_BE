package com.englow3.translation.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.shared.page.PageResponse;
import com.englow3.shared.page.StablePage;
import com.englow3.translation.dto.command.SubmitTranslationCommand;
import com.englow3.translation.dto.request.SubmitTranslationRequest;
import com.englow3.translation.dto.response.TranslationAttemptResponse;
import com.englow3.translation.dto.response.TranslationSetDetailResponse;
import com.englow3.translation.dto.response.TranslationSetSummaryResponse;
import com.englow3.translation.service.TranslationService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Vietnamese-to-English translation practice, as a learner sees it. */
@RestController
@RequestMapping("/api/translation")
@RequiredArgsConstructor
class TranslationController {

    private final TranslationService translationService;

    @GetMapping("/sets")
    ResponseEntity<PageResponse<TranslationSetSummaryResponse>> sets(@RequestParam(required = false) String topic,
            @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                PageResponse.from(translationService.searchSets(topic, title, StablePage.of(pageable, Sort.by("title")))
                        .map(TranslationSetSummaryResponse::from)));
    }

    @GetMapping("/sets/{id}")
    ResponseEntity<TranslationSetDetailResponse> set(@PathVariable UUID id) {
        return ResponseEntity.ok(TranslationSetDetailResponse.from(translationService.setDetail(id)));
    }

    @PostMapping("/items/{id}/attempts")
    @ApiResponse(responseCode = "202", description = "Translation recorded; grading has been queued")
    @ApiResponse(responseCode = "409", description = "Today's AI allowance is spent (TRANSLATION_DAILY_LIMIT_REACHED)")
    ResponseEntity<TranslationAttemptResponse> submit(@PathVariable UUID id,
            @Valid @RequestBody SubmitTranslationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(TranslationAttemptResponse
                .from(translationService.submit(new SubmitTranslationCommand(id, request.response()))));
    }

    @GetMapping("/attempts/{id}")
    ResponseEntity<TranslationAttemptResponse> attempt(@PathVariable UUID id) {
        return ResponseEntity.ok(TranslationAttemptResponse.from(translationService.attempt(id)));
    }

    @GetMapping("/attempts")
    ResponseEntity<PageResponse<TranslationAttemptResponse>> history(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity
                .ok(PageResponse.from(translationService.history(pageable).map(TranslationAttemptResponse::from)));
    }
}
