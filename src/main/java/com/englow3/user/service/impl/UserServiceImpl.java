package com.englow3.user.service.impl;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.dto.command.UpdateUserBasicInfoCommand;
import com.englow3.user.dto.result.UserInformationResult;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;
import com.englow3.user.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of("image/png", "png", "image/jpeg", "jpg",
            "image/webp", "webp");

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final UserRepository userRepo;
    private final CurrentUser currentUser;
    private final ObjectStorageClient objectStorageClient;
    private final String avatarBucket;

    public UserServiceImpl(UserRepository userRepo, CurrentUser currentUser, ObjectStorageClient objectStorageClient,
            @Value("${app.storage.avatar-bucket}") String avatarBucket) {
        this.userRepo = userRepo;
        this.currentUser = currentUser;
        this.objectStorageClient = objectStorageClient;
        this.avatarBucket = avatarBucket;
    }

    @Transactional(readOnly = true)
    public UserInformationResult me() {
        return UserInformationResult.of(requireCurrentUser());
    }

    @Transactional
    public UserInformationResult updateBasicInfo(UpdateUserBasicInfoCommand command) {
        User user = requireCurrentUser();

        user.changeFullName(command.fullName());
        user.rename(command.displayName());
        user.changeGender(command.gender());
        user.changeBirthDate(command.birthDate());

        return UserInformationResult.of(user);
    }

    public UserInformationResult changeAvatar(MultipartFile image) {
        return storeImage(image, "avatar");
    }

    public UserInformationResult changeBanner(MultipartFile image) {
        return storeImage(image, "banner");
    }

    private UserInformationResult storeImage(MultipartFile image, String kind) {
        User user = requireCurrentUser();
        String objectKey = upload(image, user.getId(), kind);

        String replaced;
        if ("avatar".equals(kind)) {
            replaced = user.getAvatarObjectKey();
            user.changeAvatar(objectKey);
        } else {
            replaced = user.getBannerObjectKey();
            user.changeBanner(objectKey);
        }

        UserInformationResult result = UserInformationResult.of(userRepo.save(user));
        deleteQuietly(replaced);
        return result;
    }

    /**
     * The picture that was just replaced. Every change used to leave the old file in the bucket for good. Only once the
     * new key is saved, and best effort: a delete that fails leaves one stray file, which is no reason to fail a change
     * that has already happened.
     */
    private void deleteQuietly(String objectKey) {
        if (objectKey == null) {
            return;
        }
        try {
            objectStorageClient.delete(avatarBucket, objectKey);
        } catch (RuntimeException failure) {
            log.warn("Could not delete replaced profile image {}", objectKey, failure);
        }
    }

    private String upload(MultipartFile image, UUID userId, String kind) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException("IMAGE_REQUIRED", "No image was uploaded");
        }
        // The request limit is sized for exam audio; a profile picture has no business being twelve megabytes.
        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new BadRequestException("IMAGE_TOO_LARGE", "Profile images can be at most 5 MB");
        }
        String extension = ALLOWED_IMAGE_TYPES.get(image.getContentType());
        if (extension == null) {
            throw new BadRequestException("IMAGE_TYPE_NOT_SUPPORTED",
                    "Only PNG, JPEG and WebP images are accepted, got %s".formatted(image.getContentType()));
        }

        String objectKey = "users/%s/%s/%s.%s".formatted(userId, kind, UUID.randomUUID(), extension);
        try {
            objectStorageClient.upload(avatarBucket, objectKey, image.getInputStream(), image.getSize(),
                    image.getContentType());
        } catch (IOException e) {
            throw new BadRequestException("IMAGE_UNREADABLE", "The uploaded image could not be read");
        }
        return objectKey;
    }

    private User requireCurrentUser() {
        UUID authProviderId = currentUser.authProviderId();
        return userRepo.findByAuthProviderId(authProviderId).orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                "No user is linked to auth provider id %s".formatted(authProviderId)));
    }
}
