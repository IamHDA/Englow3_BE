package com.englow3.user.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.NotFoundException;
import com.englow3.shared.security.CurrentUser;
import com.englow3.user.entity.User;
import com.englow3.user.repository.UserRepository;
import com.englow3.user.repository.UserTourCompletionRepository;
import com.englow3.user.service.UserTourService;

@Service
public class UserTourServiceImpl implements UserTourService {

    private static final int TOUR_VERSION = 1;

    private final UserRepository userRepo;
    private final UserTourCompletionRepository tourRepo;
    private final CurrentUser currentUser;

    public UserTourServiceImpl(UserRepository userRepo, UserTourCompletionRepository tourRepo,
            CurrentUser currentUser) {
        this.userRepo = userRepo;
        this.tourRepo = tourRepo;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean completed() {
        User user = requireCurrentUser();
        return tourRepo.existsByUserIdAndRoleAndVersion(user.getId(), user.getRole(), TOUR_VERSION);
    }

    @Override
    @Transactional
    public void complete() {
        User user = requireCurrentUser();
        tourRepo.insertIfAbsent(UUID.randomUUID(), user.getId(), user.getRole().name(), TOUR_VERSION);
    }

    private User requireCurrentUser() {
        return userRepo.findByAuthProviderId(currentUser.authProviderId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "No user is linked to this account"));
    }
}
