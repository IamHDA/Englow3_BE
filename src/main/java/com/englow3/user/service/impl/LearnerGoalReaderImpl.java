package com.englow3.user.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.user.api.LearnerGoalReader;
import com.englow3.user.entity.LearnerProfile;
import com.englow3.user.entity.LearningPurpose;
import com.englow3.user.entity.User;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.repository.LearningPurposeRepository;
import com.englow3.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LearnerGoalReaderImpl implements LearnerGoalReader {
    private final UserRepository userRepo;
    private final LearnerProfileRepository learnerProfileRepo;
    private final LearningPurposeRepository learningPurposeRepo;

    @Override
    @Transactional(readOnly = true)
    public Optional<Goal> read(UUID userId) {
        Optional<User> user = userRepo.findById(userId);
        Optional<LearnerProfile> profile = learnerProfileRepo.findByUserId(userId);
        if (user.isEmpty() || profile.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(goal(user.get(), profile.get()));
    }

    private Goal goal(User user, LearnerProfile profile) {
        List<LearningPurpose> purposes = learningPurposeRepo.findAllById(user.getLearningPurposeIds());
        boolean certificateLearner = purposes.stream()
                .anyMatch(purpose -> LearningPurpose.CERTIFICATE_CODE.equals(purpose.getPurposeCode()));
        return new Goal(user.getId(), name(profile.getCurrentLevel()), certificateLearner,
                name(profile.getTargetCertificateType()), profile.getCurrentScore(), profile.getTargetScore(),
                name(profile.getTargetLevel()), profile.getTargetDate(), profile.getDailyStudyMinutes(),
                purposes.stream().map(LearningPurpose::getPurposeCode).sorted().toList(),
                user.getTargetSkills().stream().map(Enum::name).sorted().toList());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
