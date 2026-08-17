package com.englow3.user.dto.command;

import java.util.Set;

import com.englow3.user.entity.TargetSkill;

public record SelectTargetSkillsCommand(Set<TargetSkill> skills) {
}
