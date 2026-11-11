package com.englow3.exam.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.englow3.exam.dto.command.ConfigureToeicScoresCommand;
import com.englow3.exam.dto.request.ConfigureToeicScoresRequest;
import com.englow3.exam.dto.response.ScoreConversionResponse;
import com.englow3.exam.service.ScoreConfigurationService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/exam-score-conversions/toeic")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ScoreConfigurationController {
    private final ScoreConfigurationService service;

    @GetMapping
    public ResponseEntity<List<ScoreConversionResponse>> read() {
        return ResponseEntity.ok(service.toeic().stream().map(ScoreConversionResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<List<ScoreConversionResponse>> configure(
            @Valid @RequestBody ConfigureToeicScoresRequest request) {
        return ResponseEntity.ok(service
                .configure(new ConfigureToeicScoresCommand(request.effectiveFrom(), request.sourceReference(),
                        request.rows().stream()
                                .map(r -> new ConfigureToeicScoresCommand.Row(r.sectionType(), r.rawScoreFrom(),
                                        r.rawScoreTo(), r.convertedScore()))
                                .toList()))
                .stream().map(ScoreConversionResponse::from).toList());
    }
}
