package com.englow3.exam.dto.result;

import java.util.Map;
import java.util.UUID;

import com.englow3.exam.dto.command.CreateExamCommand;
import com.englow3.exam.dto.command.UpdateExamContentCommand;

public record AuthoringResult(UUID id, long version, String status, String reviewNote, CreateExamCommand metadata,
        UpdateExamContentCommand content, Map<String, String> media) {
}
