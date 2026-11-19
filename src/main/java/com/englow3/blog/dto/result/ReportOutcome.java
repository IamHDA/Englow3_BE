package com.englow3.blog.dto.result;

/** {@code created} is false when the reader already had an open report for this content and this is it. */
public record ReportOutcome(BlogReportResult report, boolean created) {
}
