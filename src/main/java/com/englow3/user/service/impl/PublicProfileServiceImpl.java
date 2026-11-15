package com.englow3.user.service.impl;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.NotFoundException;
import com.englow3.user.dto.result.PublicProfileResult;
import com.englow3.user.entity.Role;
import com.englow3.user.entity.User;
import com.englow3.user.helper.AvatarPreset;
import com.englow3.user.repository.UserRepository;
import com.englow3.user.service.PublicProfileService;

/**
 * Hand-written constructor for the same reason as {@link PublicProfileReaderImpl}: Lombok does not copy {@code @Value},
 * and the two base URLs are what turn stored keys into addresses.
 */
@Service
public class PublicProfileServiceImpl implements PublicProfileService {

    private final UserRepository userRepo;
    private final String publicBaseUrl;
    private final String presetBaseUrl;

    public PublicProfileServiceImpl(UserRepository userRepo,
            @Value("${app.storage.public-base-url}") String publicBaseUrl,
            @Value("${app.avatar.preset-base-url}") String presetBaseUrl) {
        this.userRepo = userRepo;
        this.publicBaseUrl = publicBaseUrl;
        this.presetBaseUrl = presetBaseUrl;
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProfileResult wall(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No such user"));
        boolean staff = user.getRole() == Role.ADMIN || user.getRole() == Role.STAFF;
        // The stored key is the whole path, so the banner address is one concatenation, as for the account's own page.
        String bannerUrl = user.getBannerObjectKey() == null ? null : publicBaseUrl + "/" + user.getBannerObjectKey();
        return new PublicProfileResult(user.getId(), user.getDisplayName(),
                AvatarPreset.urlForStoredKey(user.getAvatarObjectKey(), presetBaseUrl, publicBaseUrl), bannerUrl, staff,
                user.getCreatedAt());
    }
}
