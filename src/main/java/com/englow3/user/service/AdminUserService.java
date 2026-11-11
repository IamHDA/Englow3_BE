package com.englow3.user.service;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.englow3.user.entity.Role;
import com.englow3.user.dto.result.AdminUserResult;
import com.englow3.user.dto.result.UserRoleChangeResult;

public interface AdminUserService {
    Page<AdminUserResult> search(String term, Role role, Pageable page);

    AdminUserResult changeRole(UUID id, Role expected, Role replacement, String reason);

    List<UserRoleChangeResult> roleHistory(UUID id);
}
