package com.englow3.blog.dto.request;

import com.englow3.blog.dto.command.ReportContentCommand;
import com.englow3.blog.entity.BlogReportReason;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code detail} is an optional note of up to 500 characters; a blank one is the same as none. */
public record ReportContentRequest(@NotNull BlogReportReason reason,
        @Schema(nullable = true) @Size(max = 500) String detail) {

    public ReportContentCommand toCommand() {
        return new ReportContentCommand(reason, detail);
    }
}
