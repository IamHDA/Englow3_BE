package com.englow3.tutor.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.tutor.dto.command.ReportTutorMessageCommand;
import com.englow3.tutor.dto.command.SendTutorMessageCommand;
import com.englow3.tutor.dto.result.TutorConversationResult;
import com.englow3.tutor.dto.result.TutorConversationSummaryResult;
import com.englow3.tutor.dto.result.TutorMessageResult;
import com.englow3.tutor.service.TutorService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The tutor conversation. Every route is scoped to the caller; there is no reading somebody else's thread. */
@RestController
@RequestMapping("/api/tutor")
@RequiredArgsConstructor
public class TutorController {

    private final TutorService tutorService;

    @GetMapping("/conversations")
    public ResponseEntity<List<TutorConversationSummaryResult>> conversations() {
        return ResponseEntity.ok(tutorService.conversations());
    }

    /** One endpoint whether or not a thread exists: sending a message is one thing the learner does. */
    @PostMapping("/messages")
    public ResponseEntity<TutorConversationResult> send(@Valid @RequestBody SendTutorMessageCommand command) {
        return ResponseEntity.ok(tutorService.send(command));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<TutorConversationResult> conversation(@PathVariable UUID id) {
        return ResponseEntity.ok(tutorService.conversation(id));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<TutorConversationSummaryResult> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(tutorService.archive(id));
    }

    @PostMapping("/conversations/{conversationId}/messages/{messageId}/report")
    public ResponseEntity<TutorMessageResult> report(@PathVariable UUID conversationId, @PathVariable UUID messageId,
            @Valid @RequestBody(required = false) ReportTutorMessageCommand command) {
        return ResponseEntity.ok(tutorService.report(conversationId, messageId, command));
    }
}
