package com.englow3.blog.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.blog.entity.BlogReportReason;
import com.englow3.blog.entity.BlogReportStatus;
import com.englow3.blog.entity.BlogTargetType;

/** A reader's report as the reader sees it. {@code detail} is null when they gave no note. */
public record BlogReportResult(UUID id, BlogTargetType targetType, UUID targetId, BlogReportReason reason,
        String detail, BlogReportStatus status, Instant createdAt) {
}
