package com.englow3.ai.evaluation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.ai.evaluation.AiEvaluationDtos.DecisionRequest;
import com.englow3.ai.evaluation.AiEvaluationDtos.RunRequest;
import com.englow3.ai.evaluation.AiEvaluationDtos.RunResponse;
import com.englow3.ai.evaluation.AiEvaluationDtos.SuiteRequest;
import com.englow3.ai.evaluation.AiEvaluationDtos.SuiteResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/ai/evaluations")
@PreAuthorize("@authorization.isAdmin()")
class AiEvaluationController {

    private final AiEvaluationService service;

    AiEvaluationController(AiEvaluationService service) {
        this.service = service;
    }

    @PostMapping("/suites")
    @ResponseStatus(HttpStatus.CREATED)
    SuiteResponse createSuite(@Valid @RequestBody SuiteRequest request) {
        return service.createSuite(request);
    }

    @GetMapping("/suites")
    List<SuiteResponse> suites() {
        return service.suites();
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    RunResponse createRun(@Valid @RequestBody RunRequest request) {
        return service.createRun(request);
    }

    @GetMapping("/runs")
    List<RunResponse> runs() {
        return service.runs();
    }

    @GetMapping("/runs/{runId}")
    RunResponse run(@PathVariable UUID runId) {
        return service.run(runId);
    }

    @PostMapping("/runs/{runId}/decision")
    RunResponse decide(@PathVariable UUID runId, @Valid @RequestBody DecisionRequest request) {
        return service.decide(runId, request);
    }
}
