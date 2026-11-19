package com.englow3.chat.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.chat.dto.request.SendMessageRequest;
import com.englow3.chat.dto.response.ChatMessageResponse;
import com.englow3.chat.dto.result.SendMessageOutcome;
import com.englow3.chat.service.ChatMessageService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/** Messages of one conversation, and recalling one. */
@RestController
@RequestMapping("/api/chat")
public class ChatMessageController {

    private static final String DEFAULT_LIMIT = "50";

    private final ChatMessageService messageService;

    public ChatMessageController(ChatMessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Messages in ascending seq order. {@code afterSeq} catches up after a reconnect, {@code beforeSeq} scrolls back,
     * neither gives the newest; both together is a 400. At most 100 per call.
     */
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<ChatMessageResponse>> history(@PathVariable UUID id,
            @RequestParam(required = false) Long afterSeq, @RequestParam(required = false) Long beforeSeq,
            @RequestParam(defaultValue = DEFAULT_LIMIT) int limit) {
        return ResponseEntity.ok(messageService.history(id, afterSeq, beforeSeq, limit).stream()
                .map(ChatMessageResponse::from).toList());
    }

    @PostMapping("/conversations/{id}/messages")
    @ApiResponse(responseCode = "200", description = "This clientId was already sent: the first message is returned")
    @ApiResponse(responseCode = "201", description = "The message was sent")
    public ResponseEntity<ChatMessageResponse> send(@PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        SendMessageOutcome outcome = messageService.send(request.toCommand(id));
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ChatMessageResponse.from(outcome.message()));
    }

    @PostMapping("/messages/{messageId}/recall")
    public ResponseEntity<ChatMessageResponse> recall(@PathVariable UUID messageId) {
        return ResponseEntity.ok(ChatMessageResponse.from(messageService.recall(messageId)));
    }
}
