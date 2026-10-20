package com.englow3.user.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.englow3.user.dto.response.UserBootstrapResponse;
import com.englow3.user.service.UserBootstrapService;

@RestController
@RequestMapping("/api/user/me/bootstrap")
class UserBootstrapController {
    private final UserBootstrapService bootstrapService;
    private final String publicBaseUrl;

    UserBootstrapController(UserBootstrapService bootstrapService,
            @Value("${app.storage.public-base-url}") String publicBaseUrl) {
        this.bootstrapService = bootstrapService;
        this.publicBaseUrl = publicBaseUrl;
    }

    @GetMapping
    ResponseEntity<UserBootstrapResponse> current() {
        return ResponseEntity.ok(UserBootstrapResponse.from(bootstrapService.current(), publicBaseUrl));
    }
}
