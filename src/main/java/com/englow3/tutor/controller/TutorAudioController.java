package com.englow3.tutor.controller;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.englow3.shared.error.BadRequestException;
import com.englow3.tutor.helper.TutorWav;
import com.englow3.tutor.dto.result.TutorRecordingResult;
import com.englow3.tutor.service.TutorAudioService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tutor/recordings")
@RequiredArgsConstructor
public class TutorAudioController {
    private final TutorAudioService service;

    @PostMapping(consumes = "audio/wav")
    public ResponseEntity<TutorRecordingResult> upload(@RequestParam UUID clientMessageId,
            @RequestParam(defaultValue = "en-US") String locale, HttpServletRequest request)
            throws java.io.IOException {
        try (var permit = service.reserveUpload()) {
            if (request.getContentLengthLong() > TutorWav.MAX_BYTES)
                throw new BadRequestException("TUTOR_AUDIO_TOO_LARGE", "Record at most one minute");
            byte[] wav = request.getInputStream().readNBytes(TutorWav.MAX_BYTES + 1);
            if (wav.length > TutorWav.MAX_BYTES)
                throw new BadRequestException("TUTOR_AUDIO_TOO_LARGE", "Record at most one minute");
            return ResponseEntity.ok(service.upload(clientMessageId, locale, wav));
        }
    }

    @GetMapping("/requests/{clientMessageId}")
    public ResponseEntity<TutorRecordingResult> receipt(@PathVariable UUID clientMessageId) {
        return ResponseEntity.ok(service.receipt(clientMessageId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> discard(@PathVariable UUID id) {
        service.discard(id);
        return ResponseEntity.noContent().build();
    }
}
