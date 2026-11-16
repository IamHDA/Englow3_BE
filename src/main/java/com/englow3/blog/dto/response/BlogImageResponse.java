package com.englow3.blog.dto.response;

import java.util.UUID;

import com.englow3.blog.dto.result.BlogImageResult;

/** {@code url} is a short-lived link; the post's text refers to the image as {@code blog-image:<id>}. */
public record BlogImageResponse(UUID id, String url) {

    static BlogImageResponse from(BlogImageResult result) {
        return new BlogImageResponse(result.id(), result.url());
    }
}
