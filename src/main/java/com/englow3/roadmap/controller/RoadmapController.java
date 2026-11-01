package com.englow3.roadmap.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.roadmap.dto.request.AttemptLinkRequest;
import com.englow3.roadmap.dto.response.RoadmapResponse;
import com.englow3.roadmap.service.RoadmapService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The signed-in learner's own roadmap. No user parameter anywhere: the only roadmap anyone reaches is theirs. */
@RestController
@RequestMapping("/api/roadmap")
@RequiredArgsConstructor
public class RoadmapController {

    private final RoadmapService roadmapService;

    /** 204 when the learner has never had a roadmap. */
    @GetMapping
    public ResponseEntity<RoadmapResponse> current() {
        return roadmapService.current().map(RoadmapResponse::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public ResponseEntity<RoadmapResponse> create() {
        return ResponseEntity.ok(RoadmapResponse.from(roadmapService.create()));
    }

    @DeleteMapping
    public ResponseEntity<Void> abandon() {
        roadmapService.abandon();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/milestones/{milestoneId}/sittings")
    public ResponseEntity<RoadmapResponse> recordMockSitting(@PathVariable UUID milestoneId,
            @Valid @RequestBody AttemptLinkRequest request) {
        return ResponseEntity
                .ok(RoadmapResponse.from(roadmapService.recordMockSitting(milestoneId, request.attemptId())));
    }
}
