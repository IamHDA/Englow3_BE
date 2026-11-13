package com.englow3.progress.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.progress.dto.response.AdminOverviewResponse;
import com.englow3.progress.service.AdminOverviewService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/overview")
@PreAuthorize("hasAnyRole('ADMIN','STAFF')")
@RequiredArgsConstructor
class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;

    @GetMapping
    ResponseEntity<AdminOverviewResponse> overview() {
        return ResponseEntity.ok(AdminOverviewResponse.from(adminOverviewService.overview()));
    }
}
