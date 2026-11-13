package com.englow3.user.service;

import org.springframework.web.multipart.MultipartFile;

import com.englow3.user.dto.command.UpdateUserBasicInfoCommand;
import com.englow3.user.dto.result.UserInformationResult;

public interface UserService {
    UserInformationResult me();

    UserInformationResult updateBasicInfo(UpdateUserBasicInfoCommand command);

    /** Wears one of the preset pictures; refused with AVATAR_PRESET_UNKNOWN for an id that names none. */
    UserInformationResult choosePresetAvatar(String presetId);

    UserInformationResult changeBanner(MultipartFile image);
}
