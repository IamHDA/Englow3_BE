package com.englow3.ai.learningpath;

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
@RequestMapping("/api/learning-paths")
@Validated
class LearningPathController {

    private final LearningPathService service;

    LearningPathController(LearningPathService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    LearningPathDtos.PathResponse generate(@Valid @RequestBody LearningPathDtos.GenerateRequest request) {
        return service.generate(request);
    }

    @GetMapping("/current")
    LearningPathDtos.PathResponse current() {
        return service.current();
    }

    @PutMapping("/preferences")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void preferences(@Valid @RequestBody LearningPathDtos.PreferencesRequest request) {
        service.updatePreferences(request);
    }

    @PostMapping("/items/{itemId}/complete")
    LearningPathDtos.PathItem complete(@PathVariable UUID itemId,
            @Valid @RequestBody LearningPathDtos.CompleteItemRequest request) {
        return service.complete(itemId, request);
    }

    @GetMapping("/next")
    LearningPathDtos.PathItem next() {
        return service.next();
    }
}
