package com.englow3.user.controller;

import java.util.List;

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
import com.englow3.user.dto.request.ChooseAvatarRequest;
import com.englow3.user.dto.request.UpdateUserBasicInfoRequest;
import com.englow3.user.dto.response.AvatarPresetResponse;
import com.englow3.user.dto.response.UserInformationResponse;
import com.englow3.user.helper.AvatarPreset;
import com.englow3.user.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/user")
class UserController {

    private final UserService userService;
    private final String publicBaseUrl;
    private final String presetBaseUrl;

    UserController(UserService userService, @Value("${app.storage.public-base-url}") String publicBaseUrl,
            @Value("${app.avatar.preset-base-url}") String presetBaseUrl) {
        this.userService = userService;
        this.publicBaseUrl = publicBaseUrl;
        this.presetBaseUrl = presetBaseUrl;
    }

    @GetMapping("/me")
    ResponseEntity<UserInformationResponse> me() {
        return ResponseEntity.ok(response(userService.me()));
    }

    @PutMapping("/me/profile")
    ResponseEntity<UserInformationResponse> updateBasicInfo(@Valid @RequestBody UpdateUserBasicInfoRequest request) {
        UpdateUserBasicInfoCommand command = new UpdateUserBasicInfoCommand(request.fullName(), request.displayName(),
                request.gender(), request.birthDate());
        return ResponseEntity.ok(response(userService.updateBasicInfo(command)));
    }

    /** The pictures a profile can wear, in the order the picker shows them. */
    @GetMapping("/avatar-presets")
    ResponseEntity<List<AvatarPresetResponse>> avatarPresets() {
        return ResponseEntity.ok(AvatarPreset.IDS.stream()
                .map(id -> new AvatarPresetResponse(id, AvatarPreset.url(presetBaseUrl, id))).toList());
    }

    /** A profile picture is chosen from the presets; there is no upload. */
    @PutMapping("/me/profile/avatar")
    ResponseEntity<UserInformationResponse> chooseAvatar(@Valid @RequestBody ChooseAvatarRequest request) {
        return ResponseEntity.ok(response(userService.choosePresetAvatar(request.preset())));
    }

    @PutMapping(path = "/me/profile/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<UserInformationResponse> updateBanner(@RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(response(userService.changeBanner(image)));
    }

    private UserInformationResponse response(com.englow3.user.dto.result.UserInformationResult result) {
        return UserInformationResponse.from(result, publicBaseUrl, presetBaseUrl);
    }
}
