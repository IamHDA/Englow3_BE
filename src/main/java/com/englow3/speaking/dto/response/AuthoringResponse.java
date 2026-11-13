package com.englow3.speaking.dto.response;

import java.util.UUID;
import com.englow3.speaking.dto.command.CreateSpeakingPromptCommand;
import com.englow3.speaking.dto.result.AuthoringResult;

public record AuthoringResponse(UUID id, long version, String status, String reviewNote,
        CreateSpeakingPromptCommand metadata) {
    public static AuthoringResponse from(AuthoringResult r) {
        return new AuthoringResponse(r.id(), r.version(), r.status(), r.reviewNote(), r.metadata());
    }
}
