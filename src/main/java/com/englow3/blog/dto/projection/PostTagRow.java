package com.englow3.blog.dto.projection;

import java.util.UUID;

import com.englow3.blog.entity.BlogTagKind;

public record PostTagRow(UUID postId, BlogTagKind kind, String value) {
}
