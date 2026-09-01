package com.englow3.user.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.NotFoundException;
import com.englow3.user.dto.result.UserIdentityResult;
import com.englow3.user.repository.UserRepository;

/**
 * The module API other modules call to turn the auth provider id a JWT carries into this application's own user id and
 * role. It takes that id as an argument rather than reading {@code CurrentUser} itself, so the caller stays in charge
 * of whose identity it is asking for and the lookup is testable without a security context.
 */
@Service
public class UserDirectory {

    private final UserRepository userRepo;

    UserDirectory(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public UserIdentityResult resolve(UUID authProviderId) {
        return userRepo.findByAuthProviderId(authProviderId)
                .map(user -> new UserIdentityResult(user.getId(), user.getRole()))
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND",
                        "No user is linked to auth provider id %s".formatted(authProviderId)));
    }
}
