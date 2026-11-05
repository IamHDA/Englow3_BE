package com.englow3.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param preset
 *            the id of one of the profile pictures from {@code GET /api/user/avatar-presets}
 */
public record ChooseAvatarRequest(@NotBlank @Size(max = 40) String preset) {
}
