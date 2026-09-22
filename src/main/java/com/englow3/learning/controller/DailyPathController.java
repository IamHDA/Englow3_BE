package com.englow3.learning.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.learning.dto.response.DailyPathResponse;
import com.englow3.learning.service.DailyPathService;

import lombok.RequiredArgsConstructor;

/**
 * The learner's own plan for today. No path variable and no user parameter: the only path anyone can read is their own,
 * taken from the token.
 */
@RestController
@RequestMapping("/api/daily-path")
@RequiredArgsConstructor
public class DailyPathController {

    private final DailyPathService dailyPathService;

    @GetMapping
    public ResponseEntity<DailyPathResponse> dailyPath() {
        return ResponseEntity.ok(DailyPathResponse.from(dailyPathService.dailyPath()));
    }
}
