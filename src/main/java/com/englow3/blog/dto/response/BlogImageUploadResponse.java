package com.englow3.blog.dto.response;

import java.util.UUID;

import com.englow3.blog.dto.result.BlogImageUploadTicket;

/**
 * PUT the file to {@code uploadUrl} with the same content type, then write {@code reference} into the post's Markdown
 * where the image goes, and save the post.
 */
public record BlogImageUploadResponse(UUID imageId, String uploadUrl, String contentType, long expiresInSeconds,
        String reference) {

    public static BlogImageUploadResponse from(BlogImageUploadTicket ticket) {
        return new BlogImageUploadResponse(ticket.imageId(), ticket.uploadUrl(), ticket.contentType(),
                ticket.expiresInSeconds(), ticket.reference());
    }
}
