package com.englow3.exam.dto.request;

import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;

import jakarta.validation.constraints.Size;

/**
 * Bound from query parameters, so every field is optional - a null one is not a filter. An unknown enum value is
 * rejected by the converter before this record is built. The bound on {@code title} is the column width: a longer
 * needle could not match any row anyway.
 */
public record SearchExamRequest(ExamStatus status, ExamType examType, @Size(max = 100) String title) {
}
