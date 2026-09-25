package com.englow3.user.dto.request;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record SelectLearningPurposesRequest(@NotEmpty Set<@NotNull Integer> purposeIds) {
}
