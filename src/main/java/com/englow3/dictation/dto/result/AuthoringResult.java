package com.englow3.dictation.dto.result;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.englow3.dictation.dto.command.CreateDictationLessonCommand;
import com.englow3.dictation.dto.command.SaveAuthoringCommand;

public record AuthoringResult(UUID id, long version, String status, String reviewNote,
        CreateDictationLessonCommand metadata, List<SaveAuthoringCommand.Sentence> sentences,
        Map<String, String> media) {
}
