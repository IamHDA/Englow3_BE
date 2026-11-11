package com.englow3.user.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.englow3.user.entity.Role;
import com.englow3.user.dto.request.ChangeUserRoleRequest;
import com.englow3.user.dto.response.*;
import com.englow3.user.service.AdminUserService;
import com.englow3.shared.page.PageResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService service;

    @GetMapping
    public ResponseEntity<PageResponse<AdminUserResponse>> search(@RequestParam(defaultValue = "") String term,
            @RequestParam(required = false) Role role, @PageableDefault(size = 20) Pageable page) {
        return ResponseEntity.ok(PageResponse.from(service.search(term, role, page).map(AdminUserResponse::from)));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<AdminUserResponse> changeRole(@PathVariable UUID id,
            @Valid @RequestBody ChangeUserRoleRequest request) {
        return ResponseEntity.ok(AdminUserResponse
                .from(service.changeRole(id, request.expectedRole(), request.role(), request.reason())));
    }

    @GetMapping("/{id}/role-history")
    public ResponseEntity<List<UserRoleChangeResponse>> history(@PathVariable UUID id) {
        return ResponseEntity.ok(service.roleHistory(id).stream().map(UserRoleChangeResponse::from).toList());
    }
}
