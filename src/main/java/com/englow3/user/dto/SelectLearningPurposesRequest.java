package com.englow3.user.dto;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;

public record SelectLearningPurposesRequest(@NotEmpty Set<Integer> purposeIds) {
}
