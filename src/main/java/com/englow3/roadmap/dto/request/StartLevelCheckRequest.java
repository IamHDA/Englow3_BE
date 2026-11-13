package com.englow3.roadmap.dto.request;

import java.util.UUID;

import com.englow3.roadmap.entity.LevelCheckPurpose;

import jakarta.validation.constraints.NotNull;

/**
 * @param milestoneId
 *            required for a MILESTONE check, absent for ONBOARDING
 */
public record StartLevelCheckRequest(@NotNull LevelCheckPurpose purpose, UUID milestoneId) {
}
