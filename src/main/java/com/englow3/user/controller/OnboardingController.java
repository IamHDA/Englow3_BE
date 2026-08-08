package com.englow3.user.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.user.dto.LearningPurposeResponse;
import com.englow3.user.dto.OnboardingStateResponse;
import com.englow3.user.dto.SelectLearningPurposesRequest;
import com.englow3.user.dto.SelectTargetSkillsRequest;
import com.englow3.user.dto.SetCertificateTargetRequest;
import com.englow3.user.dto.SetCurrentLevelRequest;
import com.englow3.user.dto.SetLearningGoalRequest;
import com.englow3.user.service.OnboardingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping("/learning-purposes")
    ResponseEntity<List<LearningPurposeResponse>> learningPurposes() {
        return ResponseEntity.ok(onboardingService.listLearningPurposes());
    }

    @PutMapping("/learning-purposes")
    ResponseEntity<OnboardingStateResponse> selectLearningPurposes(
            @Valid @RequestBody SelectLearningPurposesRequest request) {
        return ResponseEntity.ok(onboardingService.selectLearningPurposes(request.purposeIds()));
    }

    @PutMapping("/certificate-target")
    ResponseEntity<OnboardingStateResponse> setCertificateTarget(
            @Valid @RequestBody SetCertificateTargetRequest request) {
        return ResponseEntity.ok(onboardingService.setCertificateTarget(request.certificateType()));
    }

    @PutMapping("/current-level")
    ResponseEntity<OnboardingStateResponse> setCurrentLevel(@Valid @RequestBody SetCurrentLevelRequest request) {
        return ResponseEntity.ok(onboardingService.setCurrentLevel(request.level()));
    }

    @PutMapping("/learning-goal")
    ResponseEntity<OnboardingStateResponse> setLearningGoal(@Valid @RequestBody SetLearningGoalRequest request) {
        return ResponseEntity.ok(onboardingService.setLearningGoal(request.targetScore(), request.targetDate()));
    }

    @PutMapping("/target-skills")
    ResponseEntity<OnboardingStateResponse> selectTargetSkills(@Valid @RequestBody SelectTargetSkillsRequest request) {
        return ResponseEntity.ok(onboardingService.selectTargetSkills(request.skills()));
    }

    @PostMapping("/complete")
    ResponseEntity<OnboardingStateResponse> complete() {
        return ResponseEntity.ok(onboardingService.complete());
    }
}
