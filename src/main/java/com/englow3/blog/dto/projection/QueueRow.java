package com.englow3.blog.dto.projection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.englow3.blog.entity.BlogReportReason;
import com.englow3.blog.entity.BlogTargetType;

/** One piece of content with open reports: how many, why, and when the first and the last came in. */
public record QueueRow(BlogTargetType targetType, UUID targetId, long openReports, List<BlogReportReason> reasons,
        Instant firstReportedAt, Instant lastReportedAt) {
}
