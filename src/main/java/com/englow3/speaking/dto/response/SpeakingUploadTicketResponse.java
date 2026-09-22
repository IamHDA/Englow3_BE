package com.englow3.speaking.dto.response;

import java.util.UUID;

import com.englow3.speaking.dto.result.SpeakingUploadTicket;

public record SpeakingUploadTicketResponse(UUID attemptId, String uploadUrl, String contentType,
        long expiresInSeconds) {

    public static SpeakingUploadTicketResponse from(SpeakingUploadTicket ticket) {
        return new SpeakingUploadTicketResponse(ticket.attemptId(), ticket.uploadUrl(), ticket.contentType(),
                ticket.expiresInSeconds());
    }
}
