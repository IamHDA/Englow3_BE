package com.englow3.ai.placement;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/placement/adaptive-attempts")
class AdaptivePlacementController {

    private final AdaptivePlacementService service;

    AdaptivePlacementController(AdaptivePlacementService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AdaptivePlacementDtos.AttemptResponse start(@Valid @RequestBody AdaptivePlacementDtos.StartRequest request) {
        return service.start(request);
    }

    @GetMapping("/{attemptId}")
    AdaptivePlacementDtos.AttemptResponse get(@PathVariable UUID attemptId) {
        return service.get(attemptId);
    }

    @PostMapping("/{attemptId}/answer")
    AdaptivePlacementDtos.AttemptResponse answer(@PathVariable UUID attemptId,
            @Valid @RequestBody AdaptivePlacementDtos.AnswerRequest request) {
        return service.answer(attemptId, request);
    }
}
