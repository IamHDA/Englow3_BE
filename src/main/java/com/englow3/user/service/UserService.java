package com.englow3.user.service;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.shared.storage.ObjectStorageClient;
import com.englow3.user.dto.request.UpdateUserBasicInfoRequest;
import com.englow3.user.dto.response.UserInformationResponse;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;

@Service
public class UserService {

    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of("image/png", "png", "image/jpeg", "jpg",
            "image/webp", "webp");

    private final UserRepository userRepo;
    private final CurrentUser currentUser;
    private final ObjectStorageClient objectStorageClient;
    private final String publicBucket;
    private final String publicBaseUrl;

    UserService(UserRepository userRepo, CurrentUser currentUser, ObjectStorageClient objectStorageClient,
            @Value("${app.storage.public-bucket}") String publicBucket,
            @Value("${app.storage.public-base-url}") String publicBaseUrl) {
        this.userRepo = userRepo;
        this.currentUser = currentUser;
        this.objectStorageClient = objectStorageClient;
        this.publicBucket = publicBucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional(readOnly = true)
    public UserInformationResponse me() {
        return UserInformationResponse.of(requireCurrentUser(), publicBaseUrl);
    }

    @Transactional
    public UserInformationResponse updateBasicInfo(UpdateUserBasicInfoRequest request) {
        User user = requireCurrentUser();

        user.changeFullName(request.fullName());
        user.rename(request.displayName());
        user.changeGender(request.gender());
        user.changeBirthDate(request.birthDate());

        return UserInformationResponse.of(user, publicBaseUrl);
    }

    public UserInformationResponse changeAvatar(MultipartFile image) {
        return storeImage(image, "avatar");
    }

    public UserInformationResponse changeBanner(MultipartFile image) {
        return storeImage(image, "banner");
    }

    private UserInformationResponse storeImage(MultipartFile image, String kind) {
        User user = requireCurrentUser();
        String objectKey = upload(image, user.getId(), kind);

        if ("avatar".equals(kind)) {
            user.changeAvatar(objectKey);
        } else {
            user.changeBanner(objectKey);
        }

        return UserInformationResponse.of(userRepo.save(user), publicBaseUrl);
    }

    private String upload(MultipartFile image, UUID userId, String kind) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException("IMAGE_REQUIRED", "No image was uploaded");
        }
        String extension = ALLOWED_IMAGE_TYPES.get(image.getContentType());
        if (extension == null) {
            throw new BadRequestException("IMAGE_TYPE_NOT_SUPPORTED",
                    "Only PNG, JPEG and WebP images are accepted, got %s".formatted(image.getContentType()));
        }

        String objectKey = "users/%s/%s/%s.%s".formatted(userId, kind, UUID.randomUUID(), extension);
        try {
            objectStorageClient.upload(publicBucket, objectKey, image.getInputStream(), image.getSize(),
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
