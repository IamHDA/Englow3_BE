package com.englow3.blog.dto.response;

import java.util.UUID;

import com.englow3.blog.dto.result.BlogAuthorResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record BlogAuthorResponse(UUID userId, String displayName, @Schema(nullable = true) String avatarUrl) {

    /** Null for no author: the account was erased. */
    static BlogAuthorResponse from(BlogAuthorResult result) {
        return result == null ? null
                : new BlogAuthorResponse(result.userId(), result.displayName(), result.avatarUrl());
    }
}
