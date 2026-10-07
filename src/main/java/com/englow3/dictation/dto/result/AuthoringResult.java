package com.englow3.dictation.dto.result;

import java.util.UUID;
import com.englow3.dictation.dto.command.CreateDictationLessonCommand;

public record AuthoringResult(UUID id, long version, String status, String reviewNote,
        CreateDictationLessonCommand metadata,
        java.util.List<com.englow3.dictation.dto.command.SaveAuthoringCommand.Sentence> sentences,
        java.util.Map<String, String> media) {
}
