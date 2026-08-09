package com.englow3.user.dto.request;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;

public record SelectLearningPurposesRequest(@NotEmpty Set<Integer> purposeIds) {
}
