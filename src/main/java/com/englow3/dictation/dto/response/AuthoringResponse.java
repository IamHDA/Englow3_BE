package com.englow3.dictation.dto.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.englow3.dictation.dto.command.CreateDictationLessonCommand;
import com.englow3.dictation.dto.command.SaveAuthoringCommand;
import com.englow3.dictation.dto.result.AuthoringResult;

public record AuthoringResponse(UUID id, long version, String status, String reviewNote,
        CreateDictationLessonCommand metadata, List<SaveAuthoringCommand.Sentence> sentences,
        Map<String, String> media) {
    public static AuthoringResponse from(AuthoringResult r) {
        return new AuthoringResponse(r.id(), r.version(), r.status(), r.reviewNote(), r.metadata(), r.sentences(),
                r.media());
    }
}
