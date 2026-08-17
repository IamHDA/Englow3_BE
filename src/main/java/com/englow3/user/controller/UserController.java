package com.englow3.user.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.englow3.user.dto.command.UpdateUserBasicInfoCommand;
import com.englow3.user.dto.request.UpdateUserBasicInfoRequest;
import com.englow3.user.dto.response.UserInformationResponse;
import com.englow3.user.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/user")
class UserController {

    private final UserService userService;
    private final String publicBaseUrl;

    UserController(UserService userService, @Value("${app.storage.public-base-url}") String publicBaseUrl) {
        this.userService = userService;
        this.publicBaseUrl = publicBaseUrl;
    }

    @GetMapping("/me")
    ResponseEntity<UserInformationResponse> me() {
        return ResponseEntity.ok(UserInformationResponse.from(userService.me(), publicBaseUrl));
    }

    @PutMapping("/me/profile")
    ResponseEntity<UserInformationResponse> updateBasicInfo(@Valid @RequestBody UpdateUserBasicInfoRequest request) {
        UpdateUserBasicInfoCommand command = new UpdateUserBasicInfoCommand(request.fullName(), request.displayName(),
                request.gender(), request.birthDate());
        return ResponseEntity.ok(UserInformationResponse.from(userService.updateBasicInfo(command), publicBaseUrl));
    }

    @PutMapping(path = "/me/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<UserInformationResponse> updateAvatar(@RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(UserInformationResponse.from(userService.changeAvatar(image), publicBaseUrl));
    }

    @PutMapping(path = "/me/profile/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<UserInformationResponse> updateBanner(@RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(UserInformationResponse.from(userService.changeBanner(image), publicBaseUrl));
    }
}
