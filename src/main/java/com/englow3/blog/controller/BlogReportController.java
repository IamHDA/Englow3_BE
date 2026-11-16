package com.englow3.blog.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.blog.dto.request.ReportContentRequest;
import com.englow3.blog.dto.response.BlogReportResponse;
import com.englow3.blog.dto.result.ReportOutcome;
import com.englow3.blog.service.BlogReportService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/**
 * A signed-in account reports a post or a comment it is reading. Like every route under {@code /api/blog} it needs a
 * signed-in account; what happens to a report is decided at {@code /api/admin/blog}.
 */
@RestController
@RequestMapping("/api/blog")
public class BlogReportController {

    private final BlogReportService reportService;

    public BlogReportController(BlogReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/posts/{id}/reports")
    @ApiResponse(responseCode = "200", description = "The caller already has an open report for this post: that one")
    @ApiResponse(responseCode = "201", description = "A new report was filed")
    public ResponseEntity<BlogReportResponse> reportPost(@PathVariable UUID id,
            @Valid @RequestBody ReportContentRequest request) {
        return respond(reportService.reportPost(id, request.toCommand()));
    }

    @PostMapping("/comments/{id}/reports")
    @ApiResponse(responseCode = "200", description = "The caller already has an open report for this comment: that one")
    @ApiResponse(responseCode = "201", description = "A new report was filed")
    public ResponseEntity<BlogReportResponse> reportComment(@PathVariable UUID id,
            @Valid @RequestBody ReportContentRequest request) {
        return respond(reportService.reportComment(id, request.toCommand()));
    }

    private static ResponseEntity<BlogReportResponse> respond(ReportOutcome outcome) {
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(BlogReportResponse.from(outcome.report()));
    }
}
