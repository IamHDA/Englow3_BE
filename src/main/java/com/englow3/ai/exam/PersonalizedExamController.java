package com.englow3.ai.exam;

import java.util.List;
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
@RequestMapping("/api/personalized-exams")
class PersonalizedExamController {

    private final PersonalizedExamService service;

    PersonalizedExamController(PersonalizedExamService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PersonalizedExamDtos.ExamResponse generate(@Valid @RequestBody PersonalizedExamDtos.GenerateRequest request) {
        return service.generate(request);
    }

    @GetMapping
    List<PersonalizedExamDtos.ExamResponse> history() {
        return service.history();
    }

    @GetMapping("/{examId}")
    PersonalizedExamDtos.ExamResponse get(@PathVariable UUID examId) {
        return service.get(examId);
    }
}
