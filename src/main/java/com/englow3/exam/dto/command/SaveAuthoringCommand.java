package com.englow3.exam.dto.command;

import java.util.UUID;

public record SaveAuthoringCommand(UUID id, Long version, CreateExamCommand metadata,
        UpdateExamContentCommand content) {
}
