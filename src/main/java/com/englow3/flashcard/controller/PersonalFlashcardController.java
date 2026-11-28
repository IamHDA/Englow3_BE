package com.englow3.flashcard.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.flashcard.dto.request.PersonalCardRequest;
import com.englow3.flashcard.dto.request.SavePersonalSetRequest;
import com.englow3.flashcard.dto.response.PersonalCardResponse;
import com.englow3.flashcard.dto.response.PersonalSetResponse;
import com.englow3.flashcard.service.PersonalFlashcardService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * A learner's own sets, for their owner to write and share. Listing them is {@code GET /api/flashcards/sets?scope=MINE}
 * and studying them is the ordinary study flow; this controller is only the editor.
 */
@RestController
@RequestMapping("/api/flashcards/my-sets")
@RequiredArgsConstructor
public class PersonalFlashcardController {

    private final PersonalFlashcardService personalFlashcardService;

    @PostMapping
    public ResponseEntity<PersonalSetResponse> create(@Valid @RequestBody SavePersonalSetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PersonalSetResponse.from(personalFlashcardService.createSet(request.toCommand())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PersonalSetResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(PersonalSetResponse.from(personalFlashcardService.ownSet(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PersonalSetResponse> update(@PathVariable UUID id,
            @Valid @RequestBody SavePersonalSetRequest request) {
        return ResponseEntity.ok(PersonalSetResponse.from(personalFlashcardService.updateSet(id, request.toCommand())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        personalFlashcardService.deleteSet(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cards")
    public ResponseEntity<PersonalCardResponse> addCard(@PathVariable UUID id,
            @Valid @RequestBody PersonalCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PersonalCardResponse.from(personalFlashcardService.addCard(id, request.toCommand())));
    }

    @PutMapping("/{id}/cards/{cardId}")
    public ResponseEntity<PersonalCardResponse> updateCard(@PathVariable UUID id, @PathVariable UUID cardId,
            @Valid @RequestBody PersonalCardRequest request) {
        return ResponseEntity
                .ok(PersonalCardResponse.from(personalFlashcardService.updateCard(id, cardId, request.toCommand())));
    }

    @DeleteMapping("/{id}/cards/{cardId}")
    public ResponseEntity<Void> deleteCard(@PathVariable UUID id, @PathVariable UUID cardId) {
        personalFlashcardService.deleteCard(id, cardId);
        return ResponseEntity.noContent().build();
    }
}
