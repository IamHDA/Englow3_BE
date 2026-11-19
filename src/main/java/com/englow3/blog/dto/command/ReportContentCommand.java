package com.englow3.blog.dto.command;

import com.englow3.blog.entity.BlogReportReason;

/** {@code detail} may be null or blank: the report then has no note. */
public record ReportContentCommand(BlogReportReason reason, String detail) {
}
