package com.englow3.dictation.dto.response;

import java.util.UUID;
import com.englow3.dictation.dto.command.CreateDictationLessonCommand;
import com.englow3.dictation.dto.result.AuthoringResult;

public record AuthoringResponse(UUID id, long version, String status, String reviewNote,
        CreateDictationLessonCommand metadata,
        java.util.List<com.englow3.dictation.dto.command.SaveAuthoringCommand.Sentence> sentences,
        java.util.Map<String, String> media) {
    public static AuthoringResponse from(AuthoringResult r) {
        return new AuthoringResponse(r.id(), r.version(), r.status(), r.reviewNote(), r.metadata(), r.sentences(),
                r.media());
    }
}
