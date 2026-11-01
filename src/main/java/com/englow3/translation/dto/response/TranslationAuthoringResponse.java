package com.englow3.translation.dto.response;

import java.util.List;
import java.util.UUID;

import com.englow3.translation.dto.command.SaveTranslationSetCommand;
import com.englow3.translation.dto.result.TranslationAuthoringResult;

public record TranslationAuthoringResponse(UUID id, long version, String status, String reviewNote,
        SaveTranslationSetCommand.Metadata metadata, List<SaveTranslationSetCommand.Item> items) {

    public static TranslationAuthoringResponse from(TranslationAuthoringResult r) {
        return new TranslationAuthoringResponse(r.id(), r.version(), r.status(), r.reviewNote(), r.metadata(),
                r.items());
    }
}
