package com.englow3.user.service.impl;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englow3.user.api.LearnerContextReader;
import com.englow3.user.api.UserDirectory;
import com.englow3.user.repository.LearnerProfileRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearnerContextReaderImpl implements LearnerContextReader {
    private final UserDirectory userDirectory;
    private final LearnerProfileRepository profileRepo;

    @Transactional(readOnly = true)
    public Optional<LearningContext> current() {
        return profileRepo.findByUserId(userDirectory.requireCurrentUserId())
                .map(profile -> new LearningContext(
                        profile.getCurrentLevel() == null ? null : profile.getCurrentLevel().name(),
                        profile.getPlacementAttemptId() != null,
                        profile.getTargetCertificateType() == null ? null : profile.getTargetCertificateType().name(),
                        profile.getTargetScore()));
    }
}
