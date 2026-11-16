package com.englow3.chat.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.chat.dto.request.MarkReadRequest;
import com.englow3.chat.dto.request.OpenConversationRequest;
import com.englow3.chat.dto.request.StartImageUploadRequest;
import com.englow3.chat.dto.response.ChatImageUploadResponse;
import com.englow3.chat.dto.response.ConversationSummaryResponse;
import com.englow3.chat.dto.response.CountResponse;
import com.englow3.chat.dto.result.OpenConversationOutcome;
import com.englow3.chat.service.ChatConversationService;
import com.englow3.chat.service.ChatImageService;
import com.englow3.shared.page.PageResponse;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/** A learner's conversations: open one with a friend, list them, mark them read, ask for an image upload URL. */
@RestController
@RequestMapping("/api/chat")
public class ChatConversationController {

    private static final int MAX_PAGE_SIZE = 50;

    private final ChatConversationService conversationService;
    private final ChatImageService imageService;

    public ChatConversationController(ChatConversationService conversationService, ChatImageService imageService) {
        this.conversationService = conversationService;
        this.imageService = imageService;
    }

    /** Conversations that have a message, newest first. Each carries {@code lastSeq} so a client can catch up. */
    @GetMapping("/conversations")
    public ResponseEntity<PageResponse<ConversationSummaryResponse>> conversations(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                PageResponse.from(conversationService.list(clamp(pageable)).map(ConversationSummaryResponse::from)));
    }

    @PostMapping("/conversations")
    @ApiResponse(responseCode = "200", description = "The two already had a conversation")
    @ApiResponse(responseCode = "201", description = "A new conversation was created")
    public ResponseEntity<ConversationSummaryResponse> open(@Valid @RequestBody OpenConversationRequest request) {
        OpenConversationOutcome outcome = conversationService.open(request.friendId());
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ConversationSummaryResponse.from(outcome.conversation()));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ConversationSummaryResponse> conversation(@PathVariable UUID id) {
        return ResponseEntity.ok(ConversationSummaryResponse.from(conversationService.get(id)));
    }

    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, @Valid @RequestBody MarkReadRequest request) {
        conversationService.markRead(id, request.upToSeq());
        return ResponseEntity.noContent().build();
    }

    /** A URL to PUT one image to. Send a message naming the returned {@code uploadId} once the PUT has finished. */
    @PostMapping("/conversations/{id}/image-uploads")
    public ResponseEntity<ChatImageUploadResponse> startImageUpload(@PathVariable UUID id,
            @Valid @RequestBody StartImageUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ChatImageUploadResponse.from(imageService.startUpload(request.toCommand(id))));
    }

    /** How many conversations hold something unread: the badge, not a count of messages. */
    @GetMapping("/unread-count")
    public ResponseEntity<CountResponse> unreadCount() {
        return ResponseEntity.ok(new CountResponse(conversationService.unreadConversationCount()));
    }

    /** Page number and size only: the order of the list is the conversation's last message, not the client's choice. */
    private static Pageable clamp(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
    }
}
