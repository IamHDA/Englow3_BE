package com.englow3.ai.governance;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.ai.foundation.AiCapability;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("@authorization.isAdmin()")
class AiAdminController {

    private final AiAdminService adminService;
    private final AiGovernanceService governanceService;

    AiAdminController(AiAdminService adminService, AiGovernanceService governanceService) {
        this.adminService = adminService;
        this.governanceService = governanceService;
    }

    @GetMapping("/prompts")
    List<AiGovernanceDtos.PromptSummary> prompts() {
        return adminService.prompts();
    }

    @PostMapping("/prompts")
    @ResponseStatus(HttpStatus.CREATED)
    AiGovernanceDtos.PromptSummary createPrompt(@Valid @RequestBody AiGovernanceDtos.PromptTemplateRequest request) {
        return adminService.createPrompt(request);
    }

    @PostMapping("/prompts/{templateId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    int createVersion(@PathVariable UUID templateId,
            @Valid @RequestBody AiGovernanceDtos.PromptVersionRequest request) {
        return adminService.createVersion(templateId, request);
    }

    @PostMapping("/prompts/{templateId}/versions/{version}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void activate(@PathVariable UUID templateId, @PathVariable @Min(1) int version) {
        adminService.activateVersion(templateId, version);
    }

    @GetMapping("/model-policies")
    List<AiGovernanceDtos.ModelPolicyResponse> policies() {
        return adminService.policies();
    }

    @PutMapping("/model-policies/{capability}")
    AiGovernanceDtos.ModelPolicyResponse updatePolicy(@PathVariable AiCapability capability,
            @Valid @RequestBody AiGovernanceDtos.ModelPolicyRequest request) {
        return adminService.updatePolicy(capability, request);
    }

    @GetMapping("/content")
    List<AiGovernanceDtos.DraftResponse> content(@RequestParam(required = false) String status) {
        return governanceService.drafts(status);
    }

    @PostMapping("/content/{draftId}/approve")
    AiGovernanceDtos.DraftResponse approve(@PathVariable UUID draftId,
            @Valid @RequestBody AiGovernanceDtos.ReviewRequest request) {
        return governanceService.review(draftId, true, request.reason());
    }

    @PostMapping("/content/{draftId}/reject")
    AiGovernanceDtos.DraftResponse reject(@PathVariable UUID draftId,
            @Valid @RequestBody AiGovernanceDtos.ReviewRequest request) {
        return governanceService.review(draftId, false, request.reason());
    }

    @GetMapping("/feedback")
    List<AiGovernanceDtos.FeedbackResponse> feedback(@RequestParam(required = false) String status) {
        return governanceService.reports(status);
    }

    @PutMapping("/feedback/{reportId}")
    AiGovernanceDtos.FeedbackResponse resolve(@PathVariable UUID reportId,
            @Valid @RequestBody AiGovernanceDtos.ResolveFeedbackRequest request) {
        return governanceService.resolve(reportId, request);
    }

    @GetMapping("/metrics")
    AiGovernanceDtos.AiOperationsMetrics metrics() {
        return adminService.metrics();
    }
}
