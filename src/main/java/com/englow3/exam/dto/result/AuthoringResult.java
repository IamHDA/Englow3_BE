package com.englow3.exam.dto.result;

import java.util.UUID;
import com.englow3.exam.dto.command.*;

public record AuthoringResult(UUID id, long version, String status, String reviewNote, CreateExamCommand metadata,
        UpdateExamContentCommand content, java.util.Map<String, String> media) {
}
