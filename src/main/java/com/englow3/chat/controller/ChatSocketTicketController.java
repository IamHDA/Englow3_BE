package com.englow3.chat.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.chat.dto.response.ChatSocketTicketResponse;
import com.englow3.chat.service.ChatSocketTicketService;

/**
 * The first half of opening the chat socket from a browser: a ticket, asked for over the ordinary authenticated REST
 * path (so it carries the session), then presented once in {@code /ws/chat?ticket=...}.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatSocketTicketController {

    private final ChatSocketTicketService ticketService;

    public ChatSocketTicketController(ChatSocketTicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** {@code no-store}: no proxy between here and the browser may keep a ticket. */
    @PostMapping("/socket-tickets")
    public ResponseEntity<ChatSocketTicketResponse> issue() {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ChatSocketTicketResponse.from(ticketService.issue()));
    }
}
