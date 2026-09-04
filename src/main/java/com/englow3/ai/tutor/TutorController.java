package com.englow3.ai.tutor;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai/tutor/conversations")
@Validated
class TutorController {

    private final TutorService service;

    TutorController(TutorService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TutorDtos.ConversationResponse create(@Valid @RequestBody TutorDtos.CreateConversationRequest request) {
        return service.create(request.title());
    }

    @GetMapping
    List<TutorDtos.ConversationResponse> list() {
        return service.list();
    }

    @GetMapping("/{conversationId}")
    TutorDtos.ConversationResponse get(@PathVariable UUID conversationId) {
        return service.get(conversationId);
    }

    @DeleteMapping("/{conversationId}")
    ResponseEntity<Void> archive(@PathVariable UUID conversationId) {
        service.archive(conversationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    TutorDtos.SendMessageResponse send(@PathVariable UUID conversationId,
            @Valid @RequestBody TutorDtos.SendMessageRequest request) {
        return service.send(conversationId, request);
    }

    @PostMapping("/{conversationId}/messages/{messageId}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void feedback(@PathVariable UUID conversationId, @PathVariable UUID messageId,
            @Valid @RequestBody TutorDtos.FeedbackRequest request) {
        service.feedback(conversationId, messageId, request);
    }
}
