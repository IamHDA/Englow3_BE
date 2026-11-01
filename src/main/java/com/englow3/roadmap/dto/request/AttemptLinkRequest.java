package com.englow3.roadmap.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** A sitting or answer the learner made elsewhere, to be counted here. */
public record AttemptLinkRequest(@NotNull UUID attemptId) {
}
