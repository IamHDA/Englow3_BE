package com.englow3.blog.dto.result;

import java.util.UUID;

/** What a reader sees of an author: name and picture. The author of an erased account is null where this is used. */
public record BlogAuthorResult(UUID userId, String displayName, String avatarUrl) {
}
