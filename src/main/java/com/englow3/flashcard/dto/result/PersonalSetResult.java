package com.englow3.flashcard.dto.result;

import java.util.List;
import java.util.UUID;

import com.englow3.flashcard.entity.FlashcardSetVisibility;

/**
 * A learner's own set as its owner sees it in the editor: its details, who it is shared with, and every card.
 * {@code sharedWith} lists only people who are still friends - a share row left behind by an unfriending grants
 * nothing, and is dropped the next time the owner saves.
 */
public record PersonalSetResult(UUID id, String name, String description, String topic, String targetLevel,
        FlashcardSetVisibility visibility, long version, List<Person> sharedWith, List<PersonalCardResult> cards) {

    public record Person(UUID id, String displayName, String avatarUrl) {
    }
}
