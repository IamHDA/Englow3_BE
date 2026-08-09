package com.englow3.user.dto.request;

import java.util.Set;

import com.englow3.user.entity.TargetSkill;

/** An empty set means "I don't know which skills to work on" - allowed. */
public record SelectTargetSkillsRequest(Set<TargetSkill> skills) {
}
