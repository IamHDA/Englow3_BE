package com.englow3.translation.dto.command;

import java.util.List;
import java.util.UUID;

/**
 * A whole set as the editor saves it: its details and every sentence, in order. A null id creates the set.
 */
public record SaveTranslationSetCommand(UUID id, Long version, Metadata metadata, List<Item> items) {

    public record Metadata(String slug, String title, String topic, String targetLevel, String description) {
    }

    public record Item(String sourceVi, String referenceEn, String notes) {
    }
}
