package com.englow3.chat.dto.command;

import java.util.UUID;

public record StartImageUploadCommand(UUID conversationId, String contentType, long contentLength) {
}
