package com.englow3.user.service.impl;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.user.api.PublicProfileReader;
import com.englow3.user.entity.Role;
import com.englow3.user.entity.User;
import com.englow3.user.helper.AvatarPreset;
import com.englow3.user.repository.UserRepository;

/**
 * Hand-written constructor: Lombok's {@code @RequiredArgsConstructor} does not copy {@code @Value} onto the parameters
 * (the repository has no {@code lombok.config} for it), so the two base URLs would not be injected.
 */
@Service
public class PublicProfileReaderImpl implements PublicProfileReader {

    /** Marks the escaped characters in a LIKE pattern; the same character the flashcard catalogue search uses. */
    private static final char LIKE_ESCAPE = '!';

    private final UserRepository userRepo;
    private final String publicBaseUrl;
    private final String presetBaseUrl;

    public PublicProfileReaderImpl(UserRepository userRepo,
            @Value("${app.storage.public-base-url}") String publicBaseUrl,
            @Value("${app.avatar.preset-base-url}") String presetBaseUrl) {
        this.userRepo = userRepo;
        this.publicBaseUrl = publicBaseUrl;
        this.presetBaseUrl = presetBaseUrl;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PublicProfile> profiles(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepo.findAllById(ids).stream().collect(Collectors.toMap(User::getId, this::toProfile));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLearner(UUID id) {
        return userRepo.existsByIdAndRole(id, Role.LEARNER);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Set<UUID> lockLearners(Collection<UUID> ids) {
        return ids.isEmpty() ? Set.of() : Set.copyOf(userRepo.lockLearners(ids));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PublicProfile> searchLearners(String term, Collection<UUID> excludedIds, Pageable page) {
        String pattern = "%" + escapeLike(term.trim().toLowerCase(Locale.ROOT)) + "%";
        return userRepo.searchLearners(pattern, excludedIds, page).map(this::toProfile);
    }

    private PublicProfile toProfile(User user) {
        return new PublicProfile(user.getId(), user.getDisplayName(),
                AvatarPreset.urlForStoredKey(user.getAvatarObjectKey(), presetBaseUrl, publicBaseUrl));
    }

    private static String escapeLike(String text) {
        return text.replace(LIKE_ESCAPE + "", LIKE_ESCAPE + "" + LIKE_ESCAPE).replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }
}
