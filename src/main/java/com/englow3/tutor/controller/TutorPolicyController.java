package com.englow3.tutor.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.englow3.tutor.dto.request.TutorPolicyRequest;
import com.englow3.tutor.dto.command.TutorPolicyCommand;
import com.englow3.tutor.dto.result.TutorPolicyResult;
import com.englow3.tutor.service.TutorPolicyService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/tutor/policy")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class TutorPolicyController {
    private final TutorPolicyService service;

    @GetMapping
    public ResponseEntity<TutorPolicyResult> view() {
        return ResponseEntity.ok(service.view());
    }

    @PutMapping
    public ResponseEntity<TutorPolicyResult> update(@Valid @RequestBody TutorPolicyRequest request) {
        return ResponseEntity.ok(service.update(new TutorPolicyCommand(request.accepting(), request.allowedModes(),
                request.version(), request.reason())));
    }
}
