package com.englow3.learning.controller;

import java.util.List;
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

import com.englow3.learning.entity.FlashcardSetStatus;
import com.englow3.learning.dto.command.AddFlashcardsCommand;
import com.englow3.learning.dto.command.CreateFlashcardSetCommand;
import com.englow3.learning.dto.request.AddFlashcardsRequest;
import com.englow3.learning.dto.request.CreateFlashcardSetRequest;
import com.englow3.learning.dto.request.RejectContentRequest;
import com.englow3.learning.dto.response.ContentReviewResponse;
import com.englow3.learning.dto.response.FlashcardSetResponse;
import com.englow3.shared.page.PageResponse;
import com.englow3.learning.service.AdminFlashcardService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The authoring side. It exists now, ahead of a screen to drive it, because module-map.md requires runtime content to
 * arrive through an owned backend use case - the data pipeline does not write application tables itself.
 */
@RestController
@RequestMapping("/api/admin/flashcards")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
class AdminFlashcardController {

    private final AdminFlashcardService adminFlashcardService;

    /**
     * The authoring list. Separate from the learner catalogue because that one shows published sets only - an
     * administrator with no way to see a draft has no way to review one. A null status means every status, so the same
     * endpoint serves the full list and the review queue.
     */
    @GetMapping("/sets")
    ResponseEntity<PageResponse<ContentReviewResponse>> search(
            @RequestParam(required = false) FlashcardSetStatus status, @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(
                adminFlashcardService.searchForAuthoring(status, title, pageable).map(ContentReviewResponse::from)));
    }

    @PostMapping("/sets")
    ResponseEntity<FlashcardSetResponse> createSet(@Valid @RequestBody CreateFlashcardSetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                FlashcardSetResponse.from(adminFlashcardService.createSet(new CreateFlashcardSetCommand(request.slug(),
                        request.name(), request.description(), request.topic(), request.targetLevel()))));
    }

    @PostMapping("/sets/{id}/cards")
    ResponseEntity<FlashcardSetResponse> addCards(@PathVariable UUID id,
            @Valid @RequestBody AddFlashcardsRequest request) {
        List<AddFlashcardsCommand.NewCard> cards = request.cards().stream()
                .map(card -> new AddFlashcardsCommand.NewCard(card.lemma(), card.partOfSpeech(), card.senseLabel(),
                        card.ipaUs(), card.ipaUk(), card.audioUsObjectKey(), card.audioUkObjectKey(),
                        card.definitionEn(), card.definitionVi(), card.exampleSentence(), card.exampleTranslationVi(),
                        card.mnemonicTipVi(), card.cefrLevel()))
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FlashcardSetResponse.from(adminFlashcardService.addCards(new AddFlashcardsCommand(id, cards))));
    }

    /** Staff hand a set over for review. Available from a draft or from one that came back. */
    @PostMapping("/sets/{id}/submit-for-review")
    ResponseEntity<ContentReviewResponse> submitForReview(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminFlashcardService.submitForReview(id)));
    }

    @PostMapping("/sets/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminFlashcardService.approve(id)));
    }

    @PostMapping("/sets/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> reject(@PathVariable UUID id,
            @Valid @RequestBody RejectContentRequest request) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminFlashcardService.reject(id, request.note())));
    }

    @PostMapping("/sets/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminFlashcardService.publish(id)));
    }

    @PostMapping("/sets/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ContentReviewResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ContentReviewResponse.from(adminFlashcardService.archive(id)));
    }
}
