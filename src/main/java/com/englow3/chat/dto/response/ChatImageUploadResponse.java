package com.englow3.chat.dto.response;

import java.util.UUID;

import com.englow3.chat.dto.result.ChatImageUploadTicket;

/** PUT the file to {@code uploadUrl} with the same content type, then send a message naming {@code uploadId}. */
public record ChatImageUploadResponse(UUID uploadId, String uploadUrl, String contentType, long expiresInSeconds) {

    public static ChatImageUploadResponse from(ChatImageUploadTicket ticket) {
        return new ChatImageUploadResponse(ticket.uploadId(), ticket.uploadUrl(), ticket.contentType(),
                ticket.expiresInSeconds());
    }
}
