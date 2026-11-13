package com.englow3.translation.dto.request;

import java.util.List;
import java.util.UUID;

import com.englow3.translation.dto.command.SaveTranslationSetCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SaveTranslationSetRequest(@PositiveOrZero Long version, @NotNull @Valid Metadata metadata,
        @NotNull @Size(max = 200) List<@NotNull @Valid Item> items) {

    public record Metadata(@NotBlank @Size(max = 120) String slug, @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 60) String topic,
            @Pattern(regexp = "^(A1|A2|B1|B2|C1|C2)$", message = "Target level must be a CEFR band") String targetLevel,
            @Size(max = 2000) String description) {
    }

    public record Item(@NotBlank @Size(max = 1000) String sourceVi, @NotBlank @Size(max = 1000) String referenceEn,
            @Size(max = 2000) String notes) {
    }

    public SaveTranslationSetCommand toCommand(UUID id) {
        return new SaveTranslationSetCommand(id, version,
                new SaveTranslationSetCommand.Metadata(metadata.slug(), metadata.title(), metadata.topic(),
                        metadata.targetLevel(), metadata.description()),
                items.stream().map(
                        item -> new SaveTranslationSetCommand.Item(item.sourceVi(), item.referenceEn(), item.notes()))
                        .toList());
    }
}
