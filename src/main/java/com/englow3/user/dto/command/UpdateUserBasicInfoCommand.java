package com.englow3.user.dto.command;

import java.time.LocalDate;

import com.englow3.user.entity.Gender;

public record UpdateUserBasicInfoCommand(String fullName, String displayName, Gender gender, LocalDate birthDate) {
}
