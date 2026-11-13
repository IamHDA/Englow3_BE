package com.englow3.translation.dto.result;

import java.util.List;
import java.util.UUID;

import com.englow3.translation.dto.command.SaveTranslationSetCommand;

public record TranslationAuthoringResult(UUID id, long version, String status, String reviewNote,
        SaveTranslationSetCommand.Metadata metadata, List<SaveTranslationSetCommand.Item> items) {
}
