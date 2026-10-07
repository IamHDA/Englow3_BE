package com.englow3.exam.dto.response;

import java.util.UUID;
import com.englow3.exam.dto.command.*;
import com.englow3.exam.dto.result.AuthoringResult;

public record AuthoringResponse(UUID id, long version, String status, String reviewNote, CreateExamCommand metadata,
        UpdateExamContentCommand content, java.util.Map<String, String> media) {
    public static AuthoringResponse from(AuthoringResult r) {
        return new AuthoringResponse(r.id(), r.version(), r.status(), r.reviewNote(), r.metadata(), r.content(),
                r.media());
    }
}
