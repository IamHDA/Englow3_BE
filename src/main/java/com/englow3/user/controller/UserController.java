package com.englow3.user.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.englow3.user.dto.request.UpdateUserBasicInfoRequest;
import com.englow3.user.dto.response.UserInformationResponse;
import com.englow3.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
class UserController {

    private final UserService userService;

    @GetMapping("/me")
    ResponseEntity<UserInformationResponse> me() {
        return ResponseEntity.ok(userService.me());
    }

    @PutMapping("/me/profile")
    ResponseEntity<UserInformationResponse> updateBasicInfo(@Valid @RequestBody UpdateUserBasicInfoRequest request) {
        return ResponseEntity.ok(userService.updateBasicInfo(request));
    }

    @PutMapping(path = "/me/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<UserInformationResponse> updateAvatar(@RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(userService.changeAvatar(image));
    }

    @PutMapping(path = "/me/profile/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<UserInformationResponse> updateBanner(@RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(userService.changeBanner(image));
    }
}
