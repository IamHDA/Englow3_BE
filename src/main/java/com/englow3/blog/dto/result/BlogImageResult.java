package com.englow3.blog.dto.result;

import java.util.UUID;

/** An image of a post with a short-lived link to read it; {@code id} is what the post's text refers to. */
public record BlogImageResult(UUID id, String url) {
}
