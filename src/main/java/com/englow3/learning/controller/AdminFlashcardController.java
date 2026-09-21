package com.englow3.learning.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.learning.dto.command.CreateFlashcardSetCommand;
import com.englow3.learning.dto.request.CreateFlashcardSetRequest;
import com.englow3.learning.dto.response.FlashcardSetResponse;
import com.englow3.learning.service.AdminFlashcardService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The authoring side. It exists now, ahead of a screen to drive it, because module-map.md requires runtime content to
 * arrive through an owned backend use case - the data pipeline does not write application tables itself.
 */
@RestController
@RequestMapping("/api/admin/flashcards")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
class AdminFlashcardController {

    private final AdminFlashcardService adminFlashcardService;

    @PostMapping("/sets")
    ResponseEntity<FlashcardSetResponse> createSet(@Valid @RequestBody CreateFlashcardSetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                FlashcardSetResponse.from(adminFlashcardService.createSet(new CreateFlashcardSetCommand(request.slug(),
                        request.name(), request.description(), request.topic(), request.targetLevel()))));
    }

    @PostMapping("/sets/{id}/publish")
    ResponseEntity<FlashcardSetResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(FlashcardSetResponse.from(adminFlashcardService.publish(id)));
    }

    @PostMapping("/sets/{id}/archive")
    ResponseEntity<FlashcardSetResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(FlashcardSetResponse.from(adminFlashcardService.archive(id)));
    }
}
