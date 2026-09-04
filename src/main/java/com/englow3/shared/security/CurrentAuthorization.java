package com.englow3.shared.security;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.user.repository.UserRepository;

@Component("authorization")
public class CurrentAuthorization {

    private final CurrentUser currentUser;
    private final UserRepository userRepository;

    CurrentAuthorization(CurrentUser currentUser, UserRepository userRepository) {
        this.currentUser = currentUser;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public boolean isStaff() {
        return hasAnyRole("STAFF", "CONTENT_REVIEWER", "ADMIN");
    }

    @Transactional(readOnly = true)
    public boolean isReviewer() {
        return hasAnyRole("CONTENT_REVIEWER", "ADMIN");
    }

    @Transactional(readOnly = true)
    public boolean isAdmin() {
        return hasAnyRole("ADMIN");
    }

    private boolean hasAnyRole(String... allowed) {
        return userRepository.findByAuthProviderId(currentUser.authProviderId()).map(user -> {
            for (String role : allowed) {
                if (role.equals(user.getRole())) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }
}
