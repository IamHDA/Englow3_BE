package com.englow3.chat.dto.result;

import java.util.UUID;

public record ChatImageUploadTicket(UUID uploadId, String uploadUrl, String contentType, long expiresInSeconds) {
}
