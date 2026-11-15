package com.englow3.friendship.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.friendship.dto.response.BlockedUserResponse;
import com.englow3.friendship.service.BlockService;
import com.englow3.shared.page.PageResponse;

/** The learners the caller has blocked. */
@RestController
@RequestMapping("/api/friends/blocks")
public class BlockController {

    private static final int MAX_PAGE_SIZE = 50;

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<BlockedUserResponse>> blocked(@PageableDefault(size = 20) Pageable pageable) {
        Pageable clamped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
        return ResponseEntity.ok(PageResponse.from(blockService.blocked(clamped).map(BlockedUserResponse::from)));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Void> block(@PathVariable UUID userId) {
        blockService.block(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unblock(@PathVariable UUID userId) {
        blockService.unblock(userId);
        return ResponseEntity.noContent().build();
    }
}
