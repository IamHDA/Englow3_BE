package com.englow3.ai.placement;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/placement")
@Validated
class PlacementController {

    private final PlacementService service;

    PlacementController(PlacementService service) {
        this.service = service;
    }

    @GetMapping("/exams")
    List<PlacementDtos.ExamSummary> exams() {
        return service.availableExams();
    }

    @PostMapping("/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    PlacementDtos.StartAttemptResponse start(@Valid @RequestBody PlacementDtos.StartAttemptRequest request) {
        return service.start(request.examId());
    }

    @GetMapping("/attempts/{attemptId}/questions")
    List<PlacementDtos.QuestionResponse> questions(@PathVariable UUID attemptId) {
        return service.questions(attemptId);
    }

    @PutMapping("/attempts/{attemptId}/answer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void answer(@PathVariable UUID attemptId, @Valid @RequestBody PlacementDtos.SubmitAnswerRequest request) {
        service.answer(attemptId, request);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    PlacementDtos.SubmitAttemptResponse submit(@PathVariable UUID attemptId,
            @Valid @RequestBody PlacementDtos.SubmitAttemptRequest request) {
        return service.submit(attemptId, request.idempotencyKey());
    }

    @GetMapping("/attempts/{attemptId}/result")
    PlacementDtos.AttemptResult result(@PathVariable UUID attemptId) {
        return service.result(attemptId);
    }
}
