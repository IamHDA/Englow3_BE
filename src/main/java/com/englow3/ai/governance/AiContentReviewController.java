package com.englow3.ai.governance;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reviewer/ai/content")
@PreAuthorize("@authorization.isStaff()")
class AiContentReviewController {

    private final AiGovernanceService service;

    AiContentReviewController(AiGovernanceService service) {
        this.service = service;
    }

    @GetMapping
    List<AiGovernanceDtos.DraftResponse> content(@RequestParam(required = false) String status) {
        return service.drafts(status == null ? "PENDING_REVIEW" : status);
    }

    @PostMapping("/{draftId}/approve")
    AiGovernanceDtos.DraftResponse approve(@PathVariable UUID draftId,
            @Valid @RequestBody AiGovernanceDtos.ReviewRequest request) {
        return service.review(draftId, true, request.reason());
    }

    @PostMapping("/{draftId}/reject")
    AiGovernanceDtos.DraftResponse reject(@PathVariable UUID draftId,
            @Valid @RequestBody AiGovernanceDtos.ReviewRequest request) {
        return service.review(draftId, false, request.reason());
    }

    @PostMapping("/{draftId}/publish")
    AiGovernanceDtos.DraftResponse publish(@PathVariable UUID draftId) {
        return service.publish(draftId);
    }

    @PostMapping("/{draftId}/archive")
    AiGovernanceDtos.DraftResponse archive(@PathVariable UUID draftId,
            @Valid @RequestBody AiGovernanceDtos.ReviewRequest request) {
        return service.archive(draftId, request.reason());
    }
}
