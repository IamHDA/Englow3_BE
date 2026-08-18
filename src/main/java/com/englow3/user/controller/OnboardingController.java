package com.englow3.user.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.user.dto.command.SelectLearningPurposesCommand;
import com.englow3.user.dto.command.SelectTargetSkillsCommand;
import com.englow3.user.dto.command.SetCertificateTargetCommand;
import com.englow3.user.dto.command.SetCurrentLevelCommand;
import com.englow3.user.dto.command.SetLearningGoalCommand;
import com.englow3.user.dto.response.LearningPurposeResponse;
import com.englow3.user.dto.response.OnboardingStateResponse;
import com.englow3.user.dto.request.SelectLearningPurposesRequest;
import com.englow3.user.dto.request.SelectTargetSkillsRequest;
import com.englow3.user.dto.request.SetCertificateTargetRequest;
import com.englow3.user.dto.request.SetCurrentLevelRequest;
import com.englow3.user.dto.request.SetLearningGoalRequest;
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
        return ResponseEntity
                .ok(onboardingService.listLearningPurposes().stream().map(LearningPurposeResponse::from).toList());
    }

    @GetMapping("/current-state")
    ResponseEntity<OnboardingStateResponse> currentState() {
        return ResponseEntity.ok(OnboardingStateResponse.from(onboardingService.currentState()));
    }

    @PutMapping("/learning-purposes")
    ResponseEntity<OnboardingStateResponse> selectLearningPurposes(
            @Valid @RequestBody SelectLearningPurposesRequest request) {
        return ResponseEntity.ok(OnboardingStateResponse.from(
                onboardingService.selectLearningPurposes(new SelectLearningPurposesCommand(request.purposeIds()))));
    }

    @PutMapping("/certificate-target")
    ResponseEntity<OnboardingStateResponse> setCertificateTarget(
            @Valid @RequestBody SetCertificateTargetRequest request) {
        return ResponseEntity.ok(OnboardingStateResponse.from(
                onboardingService.setCertificateTarget(new SetCertificateTargetCommand(request.certificateType()))));
    }

    @PutMapping("/current-level")
    ResponseEntity<OnboardingStateResponse> setCurrentLevel(@Valid @RequestBody SetCurrentLevelRequest request) {
        return ResponseEntity.ok(OnboardingStateResponse
                .from(onboardingService.setCurrentLevel(new SetCurrentLevelCommand(request.level()))));
    }

    @PutMapping("/learning-goal")
    ResponseEntity<OnboardingStateResponse> setLearningGoal(@Valid @RequestBody SetLearningGoalRequest request) {
        return ResponseEntity.ok(OnboardingStateResponse.from(onboardingService.setLearningGoal(
                new SetLearningGoalCommand(request.certificateType(), request.targetScore(), request.targetDate()))));
    }

    @PutMapping("/target-skills")
    ResponseEntity<OnboardingStateResponse> selectTargetSkills(@Valid @RequestBody SelectTargetSkillsRequest request) {
        return ResponseEntity.ok(OnboardingStateResponse
                .from(onboardingService.selectTargetSkills(new SelectTargetSkillsCommand(request.skills()))));
    }

    @PostMapping("/complete")
    ResponseEntity<OnboardingStateResponse> complete() {
        return ResponseEntity.ok(OnboardingStateResponse.from(onboardingService.complete()));
    }
}
