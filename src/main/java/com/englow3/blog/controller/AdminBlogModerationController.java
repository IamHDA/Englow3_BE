package com.englow3.blog.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.blog.dto.command.ModerationQueueFilter;
import com.englow3.blog.dto.request.ModerationActionRequest;
import com.englow3.blog.dto.response.ModerationActionResponse;
import com.englow3.blog.dto.response.ModerationQueueItemResponse;
import com.englow3.blog.dto.response.ModerationTargetResponse;
import com.englow3.blog.entity.BlogQueueSort;
import com.englow3.blog.entity.BlogTargetType;
import com.englow3.blog.service.BlogModerationService;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.page.PageResponse;

import jakarta.validation.Valid;

/**
 * Moderation of blog posts and comments, for Staff and Admin only: the class carries the role check, and the security
 * configuration closes everything under {@code /api/admin} to anyone else as a second gate.
 */
@RestController
@RequestMapping("/api/admin/blog")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
public class AdminBlogModerationController {

    private final BlogModerationService moderationService;

    public AdminBlogModerationController(BlogModerationService moderationService) {
        this.moderationService = moderationService;
    }

    /**
     * Content with open reports, one entry for each however many reports it has: oldest first, or with
     * {@code sort=MOST_REPORTED} the most reported first. {@code type} limits it to posts or to comments.
     * <p>
     * Paging is {@code page} and {@code size} read here rather than as a {@code Pageable}: Spring Data would also read
     * {@code sort} as a property to order by, and here {@code sort} names one of two orders.
     */
    @GetMapping("/queue")
    public ResponseEntity<PageResponse<ModerationQueueItemResponse>> queue(
            @RequestParam(required = false) BlogTargetType type,
            @RequestParam(defaultValue = "OLDEST") BlogQueueSort sort, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest paging = PageRequest.of(Math.max(0, page), Math.max(1, size));
        return ResponseEntity.ok(PageResponse.from(moderationService
                .queue(new ModerationQueueFilter(type, sort), paging).map(ModerationQueueItemResponse::from)));
    }

    /** {@code type} is {@code post} or {@code comment}. */
    @GetMapping("/targets/{type}/{id}")
    public ResponseEntity<ModerationTargetResponse> target(@PathVariable String type, @PathVariable UUID id) {
        return ResponseEntity.ok(ModerationTargetResponse.from(moderationService.target(targetType(type), id)));
    }

    /** {@code type} is {@code post} or {@code comment}. Answers with the content as it is afterwards. */
    @PostMapping("/targets/{type}/{id}/actions")
    public ResponseEntity<ModerationTargetResponse> act(@PathVariable String type, @PathVariable UUID id,
            @Valid @RequestBody ModerationActionRequest request) {
        return ResponseEntity
                .ok(ModerationTargetResponse.from(moderationService.act(targetType(type), id, request.toCommand())));
    }

    /** What moderators did, newest first. */
    @GetMapping("/actions")
    public ResponseEntity<PageResponse<ModerationActionResponse>> actions(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest paging = PageRequest.of(Math.max(0, page), Math.max(1, size));
        return ResponseEntity
                .ok(PageResponse.from(moderationService.recentActions(paging).map(ModerationActionResponse::from)));
    }

    /** The path says it in lower case, the way a URL does; anything but the two words is refused. */
    private static BlogTargetType targetType(String segment) {
        return switch (segment) {
            case "post" -> BlogTargetType.POST;
            case "comment" -> BlogTargetType.COMMENT;
            default -> throw new BadRequestException("BLOG_TARGET_TYPE_INVALID",
                    "The type of content is 'post' or 'comment'");
        };
    }
}
