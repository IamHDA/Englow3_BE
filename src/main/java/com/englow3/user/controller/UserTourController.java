package com.englow3.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.user.dto.response.UserTourStatusResponse;
import com.englow3.user.service.UserTourService;

@RestController
@RequestMapping("/api/user/me/tour")
class UserTourController {

    private final UserTourService tourService;

    UserTourController(UserTourService tourService) {
        this.tourService = tourService;
    }

    @GetMapping
    ResponseEntity<UserTourStatusResponse> status() {
        return ResponseEntity.ok(new UserTourStatusResponse(tourService.completed()));
    }

    @PutMapping
    ResponseEntity<UserTourStatusResponse> complete() {
        tourService.complete();
        return ResponseEntity.ok(new UserTourStatusResponse(true));
    }
}
