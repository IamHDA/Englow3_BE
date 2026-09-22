package com.englow3.speaking.controller;

import java.util.List;
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
import com.englow3.speaking.dto.request.StartSpeakingAttemptRequest;
import com.englow3.speaking.dto.response.SpeakingAttemptResponse;
import com.englow3.speaking.dto.response.SpeakingPromptResponse;
import com.englow3.speaking.dto.response.SpeakingUploadTicketResponse;
import com.englow3.speaking.service.SpeakingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Speaking practice for a learner.
 * <p>
 * Recording takes three calls rather than one upload: open an attempt and get a presigned URL, PUT the audio straight
 * to storage, then say it is there. The audio never passes through this application, which is the point - a minute of
 * WAV through a request thread costs a thread for a minute and lands in the same bucket either way.
 */
@RestController
@RequestMapping("/api/speaking")
@RequiredArgsConstructor
public class SpeakingController {

    private final SpeakingService speakingService;

    @GetMapping("/prompts")
    public ResponseEntity<PageResponse<SpeakingPromptResponse>> prompts(@RequestParam(required = false) String category,
            @RequestParam(required = false) String title,
            @PageableDefault(size = 20, sort = "title", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse
                .from(speakingService.searchPublished(category, title, pageable).map(SpeakingPromptResponse::from)));
    }

    @GetMapping("/prompts/{id}")
    public ResponseEntity<SpeakingPromptResponse> prompt(@PathVariable UUID id) {
        return ResponseEntity.ok(SpeakingPromptResponse.from(speakingService.promptDetail(id)));
    }

    /** Opens an attempt and hands back somewhere to put the recording. */
    @PostMapping("/prompts/{id}/attempts")
    public ResponseEntity<SpeakingUploadTicketResponse> startAttempt(@PathVariable UUID id,
            @Valid @RequestBody StartSpeakingAttemptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SpeakingUploadTicketResponse.from(speakingService.startAttempt(id, request.contentType())));
    }

    /** The upload is done; queue the assessment. Refused if the recording is not actually in storage. */
    @PostMapping("/attempts/{id}/submit")
    public ResponseEntity<SpeakingAttemptResponse> submitAttempt(@PathVariable UUID id) {
        return ResponseEntity.ok(SpeakingAttemptResponse.from(speakingService.submitAttempt(id)));
    }

    /**
     * What the screen polls while it waits. Polling rather than a socket: one learner waiting a few seconds for one
     * score does not justify a connection that has to be held, load-balanced and reconnected.
     */
    @GetMapping("/attempts/{id}")
    public ResponseEntity<SpeakingAttemptResponse> attempt(@PathVariable UUID id) {
        return ResponseEntity.ok(SpeakingAttemptResponse.from(speakingService.attemptResult(id)));
    }

    @GetMapping("/prompts/{id}/attempts")
    public ResponseEntity<List<SpeakingAttemptResponse>> attemptHistory(@PathVariable UUID id) {
        return ResponseEntity
                .ok(speakingService.attemptHistory(id).stream().map(SpeakingAttemptResponse::from).toList());
    }
}
