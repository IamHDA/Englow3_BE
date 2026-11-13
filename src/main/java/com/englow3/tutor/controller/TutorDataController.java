package com.englow3.tutor.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.englow3.tutor.dto.result.TutorDataPageResult;
import com.englow3.tutor.service.TutorDataService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tutor/data")
@RequiredArgsConstructor
public class TutorDataController {
    private final TutorDataService service;

    @GetMapping("/export")
    public ResponseEntity<TutorDataPageResult> export(@RequestParam String kind,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(service.export(kind, page));
    }

    @DeleteMapping
    public ResponseEntity<Void> purge() {
        service.purge();
        return ResponseEntity.accepted().build();
    }
}
