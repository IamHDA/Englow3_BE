package com.englow3.user.service;

import org.springframework.web.multipart.MultipartFile;

import com.englow3.user.dto.command.UpdateUserBasicInfoCommand;
import com.englow3.user.dto.result.UserInformationResult;

public interface UserService {
    UserInformationResult me();

    UserInformationResult updateBasicInfo(UpdateUserBasicInfoCommand command);

    UserInformationResult changeAvatar(MultipartFile image);

    UserInformationResult changeBanner(MultipartFile image);
}
