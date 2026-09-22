package com.englow3.exam.dto.request;

import com.englow3.exam.entity.DifficultyLevel;
import com.englow3.exam.entity.SkillType;

/**
 * Bound from query parameters, so every field is optional - a null one is not a filter, same as
 * {@link SearchExamRequest}.
 */
public record SearchQuestionBankRequest(SkillType skillType, DifficultyLevel difficultyLevel, String keyword) {
}
