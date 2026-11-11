package com.englow3.user.dto.request;

import com.englow3.user.entity.Role;
import jakarta.validation.constraints.*;

public record ChangeUserRoleRequest(@NotNull Role expectedRole, @NotNull Role role,
        @NotBlank @Size(max = 2000) String reason) {
}
