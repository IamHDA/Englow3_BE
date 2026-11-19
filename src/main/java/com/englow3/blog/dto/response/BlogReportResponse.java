package com.englow3.blog.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.englow3.blog.dto.result.BlogReportResult;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code detail} is null when the reader gave no note. {@code targetType} is POST or COMMENT. */
public record BlogReportResponse(UUID id, String targetType, UUID targetId, String reason,
        @Schema(nullable = true) String detail, String status, Instant createdAt) {

    public static BlogReportResponse from(BlogReportResult result) {
        return new BlogReportResponse(result.id(), result.targetType().name(), result.targetId(),
                result.reason().name(), result.detail(), result.status().name(), result.createdAt());
    }
}
