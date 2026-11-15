package com.englow3.user.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.user.dto.response.PublicProfileResponse;
import com.englow3.user.service.PublicProfileService;

/**
 * The public side of any account, for its wall. The more specific {@code /me/...} routes of the other user controllers
 * keep precedence over {@code /{userId}/...}.
 */
@RestController
@RequestMapping("/api/user")
public class PublicProfileController {

    private final PublicProfileService publicProfileService;

    public PublicProfileController(PublicProfileService publicProfileService) {
        this.publicProfileService = publicProfileService;
    }

    @GetMapping("/{userId}/public-profile")
    public ResponseEntity<PublicProfileResponse> publicProfile(@PathVariable UUID userId) {
        return ResponseEntity.ok(PublicProfileResponse.from(publicProfileService.wall(userId)));
    }
}
