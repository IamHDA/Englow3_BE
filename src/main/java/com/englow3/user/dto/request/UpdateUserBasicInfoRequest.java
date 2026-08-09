package com.englow3.user.dto.request;

import java.time.LocalDate;

import com.englow3.user.entity.Gender;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record UpdateUserBasicInfoRequest(@NotBlank @Size(max = 60) String fullName,
        @NotBlank @Size(max = 60) String displayName, Gender gender, @Past LocalDate birthDate) {
}
