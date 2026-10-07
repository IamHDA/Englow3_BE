package com.englow3.flashcard.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import com.englow3.flashcard.service.FlashcardMediaService;
import com.englow3.flashcard.dto.response.FlashcardMediaResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/flashcards/media")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
class AdminFlashcardMediaController {
    private final FlashcardMediaService mediaService;

    @PostMapping(consumes = "multipart/form-data")
    ResponseEntity<FlashcardMediaResponse> upload(@RequestPart("file") MultipartFile file) throws java.io.IOException {
        try (var input = file.getInputStream()) {
            return ResponseEntity
                    .ok(FlashcardMediaResponse.from(mediaService.upload(input, file.getSize(), file.getContentType())));
        }
    }
}
