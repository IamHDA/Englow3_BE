package com.englow3.chat.dto.response;

import com.englow3.chat.dto.result.ChatSocketTicket;

/** Open {@code /ws/chat?ticket=...} with it within {@code expiresInSeconds}; it works once. */
public record ChatSocketTicketResponse(String ticket, long expiresInSeconds) {

    public static ChatSocketTicketResponse from(ChatSocketTicket result) {
        return new ChatSocketTicketResponse(result.ticket(), result.expiresInSeconds());
    }
}
