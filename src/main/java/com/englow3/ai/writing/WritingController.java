package com.englow3.ai.writing;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/writing")
@Validated
class WritingController {

    private final WritingAssessmentService service;

    WritingController(WritingAssessmentService service) {
        this.service = service;
    }

    @GetMapping("/tasks")
    List<WritingDtos.TaskSummary> tasks() {
        return service.tasks();
    }

    @PostMapping("/submissions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    WritingDtos.SubmissionAccepted submit(@Valid @RequestBody WritingDtos.CreateSubmissionRequest request) {
        return service.submit(request);
    }

    @GetMapping("/submissions")
    List<WritingDtos.SubmissionSummary> history() {
        return service.history();
    }

    @GetMapping("/submissions/{submissionId}")
    WritingDtos.SubmissionResult result(@PathVariable UUID submissionId) {
        return service.result(submissionId);
    }
}
