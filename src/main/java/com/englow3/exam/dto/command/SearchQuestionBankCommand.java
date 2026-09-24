package com.englow3.exam.dto.command;

import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.SkillType;

/** Every field is optional - a null one is not a filter. */
public record SearchQuestionBankCommand(SkillType skillType, DifficultyLevel difficultyLevel, String keyword) {
}
