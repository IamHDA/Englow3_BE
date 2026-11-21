package com.englow3.blog.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.blog.dto.response.BlogPostSummaryResponse;
import com.englow3.blog.dto.response.SaveStateResponse;
import com.englow3.blog.service.BlogSaveService;
import com.englow3.shared.page.PageResponse;

/**
 * The caller's saved posts: save, unsave and the list. Needs a signed-in account, like every route of the blog. The
 * methods are named apart from the {@code save} and {@code list} of other controllers: springdoc names an operation
 * after its method, and a clash renames the older operation in every client generated from the spec.
 */
@RestController
@RequestMapping("/api/blog")
public class BlogSaveController {

    private final BlogSaveService saveService;

    public BlogSaveController(BlogSaveService saveService) {
        this.saveService = saveService;
    }

    @PutMapping("/posts/{id}/save")
    public ResponseEntity<SaveStateResponse> savePost(@PathVariable UUID id) {
        return ResponseEntity.ok(SaveStateResponse.from(saveService.save(id)));
    }

    @DeleteMapping("/posts/{id}/save")
    public ResponseEntity<SaveStateResponse> unsavePost(@PathVariable UUID id) {
        return ResponseEntity.ok(SaveStateResponse.from(saveService.unsave(id)));
    }

    /** The posts the caller saved and may still read, the latest save first. */
    @GetMapping("/saved-posts")
    public ResponseEntity<PageResponse<BlogPostSummaryResponse>> listSavedPosts(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        PageRequest paging = PageRequest.of(Math.max(0, page), Math.max(1, size));
        return ResponseEntity.ok(PageResponse.from(saveService.listSaved(paging).map(BlogPostSummaryResponse::from)));
    }
}
