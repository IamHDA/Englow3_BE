package com.englow3.blog.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.blog.dto.request.StartImageUploadRequest;
import com.englow3.blog.dto.response.BlogImageUploadResponse;
import com.englow3.blog.service.BlogImageService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/** Image uploads for posts that are not saved yet. */
@RestController
@RequestMapping("/api/blog/images")
public class BlogImageController {

    private final BlogImageService imageService;

    public BlogImageController(BlogImageService imageService) {
        this.imageService = imageService;
    }

    /**
     * A URL to PUT one image to. Put the returned {@code reference} in the post's Markdown where the image goes, once
     * the PUT has finished, and save the post.
     */
    @ApiResponse(responseCode = "201", description = "An upload URL was issued")
    @PostMapping
    public ResponseEntity<BlogImageUploadResponse> startUpload(@Valid @RequestBody StartImageUploadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BlogImageUploadResponse.from(imageService.startUpload(request.toCommand())));
    }
}
