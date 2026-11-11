package com.englow3.user.service.impl;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englow3.user.service.AdminUserService;
import com.englow3.user.repository.*;
import com.englow3.user.entity.*;
import com.englow3.user.dto.result.*;
import com.englow3.user.api.UserDirectory;
import com.englow3.shared.error.*;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {
    private final UserRepository userRepo;
    private final UserRoleChangeRepository changeRepo;
    private final UserDirectory directory;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<AdminUserResult> search(String term, Role role, Pageable page) {
        String value = term == null ? "" : term.strip();
        if (value.length() > 200)
            throw new BadRequestException("SEARCH_TOO_LONG", "Search must be at most 200 characters");
        return userRepo
                .searchForAdmin("%" + value.toLowerCase(Locale.ROOT) + "%", role,
                        PageRequest.of(page.getPageNumber(), Math.min(page.getPageSize(), 50)))
                .map(AdminUserResult::of);
    }

    @Transactional
    public AdminUserResult changeRole(UUID id, Role expected, Role replacement, String reason) {
        if (expected == null || replacement == null || reason == null || reason.isBlank()
                || reason.strip().length() > 2000)
            throw new BadRequestException("ROLE_CHANGE_INVALID", "Choose both roles and explain the change");
        UUID actor = directory.requireCurrentUserId();
        userRepo.lockRoleChanges();
        if (userRepo.findById(actor).map(User::getRole).orElse(null) != Role.ADMIN)
            throw new ForbiddenException("ADMIN_REQUIRED", "Only administrators may change roles");
        if (actor.equals(id))
            throw new ConflictException("SELF_ROLE_CHANGE_NOT_ALLOWED",
                    "Ask another administrator to change your role");
        User user = userRepo.lockById(id).orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        Role previous = user.getRole();
        user.changeRole(expected, replacement);
        if (previous != replacement) {
            changeRepo.save(UserRoleChange.of(id, actor, previous, replacement, reason.strip(), clock.instant()));
            userRepo.flush();
        }
        return AdminUserResult.of(user);
    }

    @Transactional(readOnly = true)
    public List<UserRoleChangeResult> roleHistory(UUID id) {
        if (!userRepo.existsById(id))
            throw new NotFoundException("USER_NOT_FOUND", "User not found");
        return changeRepo.findTop20ByUserIdOrderByCreatedAtDesc(id).stream()
                .map(e -> new UserRoleChangeResult(e.getId(), e.getActorId(), e.getPreviousRole(), e.getNewRole(),
                        e.getReason(), e.getCreatedAt()))
                .toList();
    }
}
