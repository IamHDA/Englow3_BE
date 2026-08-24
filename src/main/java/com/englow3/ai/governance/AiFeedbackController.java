package com.englow3.ai.governance;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ai/feedback")
class AiFeedbackController {

    private final AiGovernanceService service;

    AiFeedbackController(AiGovernanceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AiGovernanceDtos.FeedbackResponse report(@Valid @RequestBody AiGovernanceDtos.FeedbackRequest request) {
        return service.report(request);
    }
}
