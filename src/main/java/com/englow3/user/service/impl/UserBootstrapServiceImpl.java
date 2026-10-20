package com.englow3.user.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.dto.result.OnboardingStateResult;
import com.englow3.user.dto.result.UserBootstrapResult;
import com.englow3.user.dto.result.UserInformationResult;
import com.englow3.user.entity.LearnerProfile;
import com.englow3.user.entity.LearningPurpose;
import com.englow3.user.entity.Role;
import com.englow3.user.repository.LearnerProfileRepository;
import com.englow3.user.repository.LearningPurposeRepository;
import com.englow3.user.repository.UserRepository;
import com.englow3.user.service.UserBootstrapService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserBootstrapServiceImpl implements UserBootstrapService {
    private final UserRepository userRepo;
    private final LearnerProfileRepository learnerProfileRepo;
    private final LearningPurposeRepository learningPurposeRepo;
    private final CurrentUser currentUser;

    @Override
    @Transactional(readOnly = true)
    public UserBootstrapResult current() {
        var user = userRepo.findBootstrapByAuthProviderId(currentUser.authProviderId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "The current account was not found"));
        var information = UserInformationResult.of(user);
        if (user.getRole() != Role.LEARNER)
            return new UserBootstrapResult(information, null);
        // A read must not create a profile row for an account that has not started onboarding.
        var profile = learnerProfileRepo.findByUserId(user.getId())
                .orElseGet(() -> LearnerProfile.forUser(user.getId()));
        boolean certificate = learningPurposeRepo.findByPurposeCode(LearningPurpose.CERTIFICATE_CODE)
                .map(purpose -> user.getLearningPurposeIds().contains(purpose.getId())).orElse(false);
        return new UserBootstrapResult(information, OnboardingStateResult.of(user, profile, certificate));
    }
}
