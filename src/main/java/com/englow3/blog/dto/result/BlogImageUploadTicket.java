package com.englow3.blog.dto.result;

import java.util.UUID;

/** {@code reference} is the text to put in the Markdown where the image goes. */
public record BlogImageUploadTicket(UUID imageId, String uploadUrl, String contentType, long expiresInSeconds,
        String reference) {
}
