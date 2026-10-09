package com.englow3.assessment.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.assessment.dto.request.AssessmentResultReadRequest;
import com.englow3.assessment.dto.response.AssessmentNotificationResponse;
import com.englow3.assessment.service.AssessmentNotificationService;
import com.englow3.shared.page.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/assessments/notifications")
@RequiredArgsConstructor
class AssessmentNotificationController {
    private final AssessmentNotificationService notificationService;

    @GetMapping
    ResponseEntity<PageResponse<AssessmentNotificationResponse>> unread(@RequestParam(defaultValue = "0") int page) {
        return ResponseEntity
                .ok(PageResponse.from(notificationService.unread(page).map(AssessmentNotificationResponse::from)));
    }

    @PostMapping("/{id}/read")
    ResponseEntity<Boolean> markRead(@PathVariable UUID id, @Valid @RequestBody AssessmentResultReadRequest request) {
        notificationService.markRead(id, request.version());
        return ResponseEntity.ok(true);
    }
}
