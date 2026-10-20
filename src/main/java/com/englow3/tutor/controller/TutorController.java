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

    @GetMapping("/conversations/{id}/memory")
    public ResponseEntity<com.englow3.tutor.dto.result.TutorMemoryResult> memory(@PathVariable UUID id) {
        return ResponseEntity.ok(tutorService.memory(id));
    }

    @PostMapping("/conversations/{id}/memory/summarize")
    public ResponseEntity<TutorConversationResult> summarize(@PathVariable UUID id,
            @Valid @RequestBody com.englow3.tutor.dto.command.TutorActionCommand request) {
        return ResponseEntity.ok(tutorService.summarize(id, request.clientMessageId()));
    }

    @DeleteMapping("/conversations/{id}/memory")
    public ResponseEntity<Void> clearMemory(@PathVariable UUID id) {
        tutorService.clearMemory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/conversations/{id}/messages/{messageId}/versions")
    public ResponseEntity<com.englow3.tutor.dto.result.TutorReplyHistoryResult> versions(@PathVariable UUID id,
            @PathVariable UUID messageId) {
        return ResponseEntity.ok(tutorService.replyHistory(id, messageId));
    }

    @PostMapping("/conversations/{id}/messages/{messageId}/select")
    public ResponseEntity<TutorConversationResult> select(@PathVariable UUID id, @PathVariable UUID messageId,
            @Valid @RequestBody com.englow3.tutor.dto.request.TutorSelectReplyRequest request) {
        return ResponseEntity
                .ok(tutorService.selectReply(id, messageId, request.revision(), request.clientMessageId()));
    }

    @PostMapping("/conversations/{id}/messages/{messageId}/branch")
    public ResponseEntity<TutorConversationResult> branch(@PathVariable UUID id, @PathVariable UUID messageId,
            @Valid @RequestBody com.englow3.tutor.dto.request.TutorBranchRequest request) {
        return ResponseEntity.ok(tutorService.branch(id, messageId, request.message(), request.clientMessageId(),
                request.personalized()));
    }

    @GetMapping("/usage")
    public ResponseEntity<com.englow3.tutor.dto.result.TutorUsageResult> usage() {
        return ResponseEntity.ok(tutorService.usage());
    }

    @GetMapping("/requests/{clientId}")
    public ResponseEntity<TutorConversationResult> receipt(@PathVariable UUID clientId) {
        return ResponseEntity.ok(tutorService.receipt(clientId));
    }

    @GetMapping("/history")
    public ResponseEntity<com.englow3.shared.page.PageResponse<TutorConversationSummaryResult>> history(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") @jakarta.validation.constraints.Size(max = 200) String search,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean archived,
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable page) {
        return ResponseEntity.ok(com.englow3.shared.page.PageResponse
                .from(tutorService.search(search, archived, com.englow3.shared.page.StablePage.of(page))));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<com.englow3.tutor.dto.result.TutorMessagePageResult> messages(@PathVariable UUID id,
            @org.springframework.data.web.PageableDefault(size = 30) org.springframework.data.domain.Pageable page) {
        return ResponseEntity.ok(tutorService.messages(id, com.englow3.shared.page.StablePage.of(page)));
    }

    @GetMapping("/conversations/{id}/messages/{messageId}")
    public ResponseEntity<TutorMessageResult> reply(@PathVariable UUID id, @PathVariable UUID messageId) {
        return ResponseEntity.ok(tutorService.reply(id, messageId));
    }

    @PostMapping("/conversations/{id}/messages/{messageId}/retry")
    public ResponseEntity<TutorConversationResult> retry(@PathVariable UUID id, @PathVariable UUID messageId,
            @Valid @RequestBody com.englow3.tutor.dto.command.TutorActionCommand command) {
        return ResponseEntity.ok(tutorService.restart(id, messageId, command.clientMessageId(), false));
    }

    @PostMapping("/conversations/{id}/messages/{messageId}/regenerate")
    public ResponseEntity<TutorConversationResult> regenerate(@PathVariable UUID id, @PathVariable UUID messageId,
            @Valid @RequestBody com.englow3.tutor.dto.command.TutorActionCommand command) {
        return ResponseEntity.ok(tutorService.restart(id, messageId, command.clientMessageId(), true));
    }

    @PostMapping("/conversations/{id}/messages/{messageId}/cancel")
    public ResponseEntity<TutorMessageResult> cancel(@PathVariable UUID id, @PathVariable UUID messageId) {
        return ResponseEntity.ok(tutorService.cancel(id, messageId));
    }

    @PostMapping("/conversations/{id}/restore")
    public ResponseEntity<TutorConversationSummaryResult> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(tutorService.restore(id));
    }

    @org.springframework.web.bind.annotation.PutMapping("/conversations/{id}/title")
    public ResponseEntity<TutorConversationSummaryResult> rename(@PathVariable UUID id,
            @Valid @RequestBody com.englow3.tutor.dto.command.RenameTutorCommand command) {
        return ResponseEntity.ok(tutorService.rename(id, command.title()));
    }

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
