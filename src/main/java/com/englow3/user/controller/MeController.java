package com.englow3.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.user.dto.MeResponse;
import com.englow3.user.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
class MeController {

    private final UserService userService;

    @GetMapping
    ResponseEntity<MeResponse> me() {
        return ResponseEntity.ok(userService.me());
    }
}
