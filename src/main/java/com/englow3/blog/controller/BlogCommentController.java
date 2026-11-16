package com.englow3.blog.controller;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.blog.dto.request.SaveCommentRequest;
import com.englow3.blog.dto.response.BlogCommentResponse;
import com.englow3.blog.service.BlogCommentService;
import com.englow3.shared.page.PageResponse;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/** Flat comments: listed and written under a post, edited and deleted by their own id. */
@RestController
@RequestMapping("/api/blog")
public class BlogCommentController {

    private final BlogCommentService commentService;

    public BlogCommentController(BlogCommentService commentService) {
        this.commentService = commentService;
    }

    /** Oldest first. The caller's own hidden or removed comments are in the list, with their {@code status}. */
    @GetMapping("/posts/{id}/comments")
    public ResponseEntity<PageResponse<BlogCommentResponse>> list(@PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.from(commentService.list(id, pageable).map(BlogCommentResponse::from)));
    }

    @ApiResponse(responseCode = "201", description = "The comment was written")
    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<BlogCommentResponse> create(@PathVariable UUID id,
            @Valid @RequestBody SaveCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BlogCommentResponse.from(commentService.create(id, request.toCommand())));
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<BlogCommentResponse> update(@PathVariable UUID commentId,
            @Valid @RequestBody SaveCommentRequest request) {
        return ResponseEntity.ok(BlogCommentResponse.from(commentService.update(commentId, request.toCommand())));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID commentId) {
        commentService.delete(commentId);
        return ResponseEntity.noContent().build();
    }
}
