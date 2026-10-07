package com.englow3.dictation.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import com.englow3.dictation.service.DictationMediaService;
import com.englow3.dictation.dto.response.DictationMediaResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/dictation/media")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
class AdminDictationMediaController {
    private final DictationMediaService mediaService;

    @PostMapping(consumes = "multipart/form-data")
    ResponseEntity<DictationMediaResponse> upload(@RequestPart("file") MultipartFile file) throws java.io.IOException {
        try (var input = file.getInputStream()) {
            return ResponseEntity
                    .ok(DictationMediaResponse.from(mediaService.upload(input, file.getSize(), file.getContentType())));
        }
    }
}
