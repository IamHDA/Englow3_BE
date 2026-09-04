package com.englow3.exam.dto.command;

import com.englow3.exam.entity.ExamStatus;
import com.englow3.exam.entity.ExamType;

/** Every field is optional - a null one is not a filter. */
public record SearchExamCommand(ExamStatus status, ExamType examType, String title) {
}
