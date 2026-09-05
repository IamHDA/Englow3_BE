package com.englow3.ai.governance;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/staff/ai/content")
@PreAuthorize("@authorization.isStaff()")
class AiContentGovernanceController {

    private final AiGovernanceService service;

    AiContentGovernanceController(AiGovernanceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    AiGovernanceDtos.DraftResponse generate(@Valid @RequestBody AiGovernanceDtos.GenerateDraftRequest request) {
        return service.generate(request);
    }

    @GetMapping
    List<AiGovernanceDtos.DraftResponse> drafts(@RequestParam(required = false) String status) {
        return service.drafts(status);
    }

    @PutMapping("/{draftId}")
    AiGovernanceDtos.DraftResponse update(@PathVariable UUID draftId,
            @Valid @RequestBody AiGovernanceDtos.UpdateDraftRequest request) {
        return service.updateDraft(draftId, request);
    }

    @PostMapping("/{draftId}/submit-review")
    AiGovernanceDtos.DraftResponse submit(@PathVariable UUID draftId) {
        return service.submitForReview(draftId);
    }
}
