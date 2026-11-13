package com.englow3.roadmap.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.roadmap.dto.command.SetLevelCheckWeightCommand;
import com.englow3.roadmap.dto.request.SetLevelCheckWeightRequest;
import com.englow3.roadmap.dto.response.LevelCheckWeightsResponse;
import com.englow3.roadmap.service.LevelCheckWeightService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The level-check weights. Changing how learners are placed is the administrator's alone, as plans are. */
@RestController
@RequestMapping("/api/admin/roadmap/level-check-weights")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminLevelCheckWeightController {

    private final LevelCheckWeightService weightService;

    @GetMapping
    public ResponseEntity<LevelCheckWeightsResponse> current() {
        return ResponseEntity.ok(LevelCheckWeightsResponse.from(weightService.current()));
    }

    @PutMapping
    public ResponseEntity<LevelCheckWeightsResponse> set(@Valid @RequestBody SetLevelCheckWeightRequest request) {
        return ResponseEntity.ok(
                LevelCheckWeightsResponse.from(weightService.set(new SetLevelCheckWeightCommand(request.purposeCode(),
                        request.vocabulary(), request.listening(), request.speaking(), request.priority()))));
    }
}
