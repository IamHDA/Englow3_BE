package com.englow3.blog.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.blog.dto.command.ListPostsCommand;
import com.englow3.blog.dto.request.SavePostRequest;
import com.englow3.blog.dto.request.UpdatePostRequest;
import com.englow3.blog.dto.response.BlogPostDetailResponse;
import com.englow3.blog.dto.response.BlogPostSummaryResponse;
import com.englow3.blog.dto.response.BlogTagCatalogueResponse;
import com.englow3.blog.dto.response.LikeStateResponse;
import com.englow3.blog.entity.BlogCertificate;
import com.englow3.blog.entity.BlogLevel;
import com.englow3.blog.entity.BlogPostSort;
import com.englow3.blog.entity.BlogSkill;
import com.englow3.blog.service.BlogLikeService;
import com.englow3.blog.service.BlogPostService;
import com.englow3.shared.page.PageResponse;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/**
 * Posts, their likes and the tag catalogue. Every route needs a signed-in account (the default of the security
 * configuration); nothing here is public.
 */
@RestController
@RequestMapping("/api/blog")
public class BlogPostController {

    private final BlogPostService postService;
    private final BlogLikeService likeService;

    public BlogPostController(BlogPostService postService, BlogLikeService likeService) {
        this.postService = postService;
        this.likeService = likeService;
    }

    /**
     * Published posts, newest first unless {@code sort=MOST_LIKED}. {@code q} searches titles (two characters at
     * least), the tag filters combine, and {@code authorId} lists one person's posts - which, when it is the caller's
     * own, also holds the hidden and removed ones with their {@code status}.
     * <p>
     * Paging is {@code page} and {@code size} read here rather than as a {@code Pageable}: Spring Data would also read
     * {@code sort} as a property to order by, and here {@code sort} names one of two orders.
     */
    @GetMapping("/posts")
    public ResponseEntity<PageResponse<BlogPostSummaryResponse>> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) BlogSkill skill, @RequestParam(required = false) BlogLevel level,
            @RequestParam(required = false) BlogCertificate certificate, @RequestParam(required = false) UUID authorId,
            @RequestParam(defaultValue = "NEWEST") BlogPostSort sort, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ListPostsCommand command = new ListPostsCommand(q, skill, level, certificate, authorId, sort);
        PageRequest paging = PageRequest.of(Math.max(0, page), Math.max(1, size));
        return ResponseEntity
                .ok(PageResponse.from(postService.list(command, paging).map(BlogPostSummaryResponse::from)));
    }

    @ApiResponse(responseCode = "201", description = "The post was created")
    @PostMapping("/posts")
    public ResponseEntity<BlogPostDetailResponse> create(@Valid @RequestBody SavePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BlogPostDetailResponse.from(postService.create(request.toCommand())));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<BlogPostDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(BlogPostDetailResponse.from(postService.get(id)));
    }

    /** Send back the {@code version} the post had when it was opened; a post that changed since is a 409. */
    @PutMapping("/posts/{id}")
    public ResponseEntity<BlogPostDetailResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdatePostRequest request) {
        return ResponseEntity.ok(BlogPostDetailResponse.from(postService.update(id, request.toCommand())));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/posts/{id}/like")
    public ResponseEntity<LikeStateResponse> like(@PathVariable UUID id) {
        return ResponseEntity.ok(LikeStateResponse.from(likeService.like(id)));
    }

    @DeleteMapping("/posts/{id}/like")
    public ResponseEntity<LikeStateResponse> unlike(@PathVariable UUID id) {
        return ResponseEntity.ok(LikeStateResponse.from(likeService.unlike(id)));
    }

    @GetMapping("/tags")
    public ResponseEntity<BlogTagCatalogueResponse> tags() {
        return ResponseEntity.ok(BlogTagCatalogueResponse.from(postService.tagCatalogue()));
    }
}
