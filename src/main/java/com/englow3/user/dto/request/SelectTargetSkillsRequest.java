package com.englow3.user.dto.request;

import java.util.Set;

import jakarta.validation.constraints.NotNull;

import com.englow3.user.entity.TargetSkill;

/**
 * An empty set means "I don't know which skills to work on" - allowed. A missing one is refused: it used to be read as
 * empty too, so a request that forgot the field silently wiped the skills the learner had chosen.
 */
public record SelectTargetSkillsRequest(@NotNull Set<@NotNull TargetSkill> skills) {
}
