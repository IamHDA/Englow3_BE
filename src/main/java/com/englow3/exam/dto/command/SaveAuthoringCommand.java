package com.englow3.exam.dto.command;

import java.util.UUID;

import com.englow3.exam.dto.request.SaveAuthoringRequest;

public record SaveAuthoringCommand(UUID id, Long version, CreateExamCommand metadata,
        UpdateExamContentCommand content) {

    /** The editor's request as a command - the one mapping the controller and the generation writer share. */
    public static SaveAuthoringCommand of(UUID id, SaveAuthoringRequest r) {
        var m = r.metadata();
        return new SaveAuthoringCommand(id, r.version(),
                new CreateExamCommand(m.title(), m.description(), m.examType(), m.certificateType(),
                        m.certificateVariant(), m.targetLevel(), m.durationSeconds(), m.maxRawScore(), m.passScore()),
                UpdateExamContentCommand.of(id, r.content()));
    }
}
