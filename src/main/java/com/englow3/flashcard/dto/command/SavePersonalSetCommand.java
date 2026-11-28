package com.englow3.flashcard.dto.command;

import java.util.List;
import java.util.UUID;

import com.englow3.flashcard.entity.FlashcardSetVisibility;

/**
 * A learner's own set as they save it. {@code friendIds} is the audience of a SELECTED_FRIENDS set and empty otherwise.
 * {@code cards} is read on create only - after that cards are edited one at a time so that nobody's progress on an
 * untouched card is disturbed. {@code version} is null on create and required on update.
 */
public record SavePersonalSetCommand(String name, String description, String topic, String targetLevel,
        FlashcardSetVisibility visibility, List<UUID> friendIds, List<PersonalCardCommand> cards, Long version) {

    public SavePersonalSetCommand {
        friendIds = friendIds == null ? List.of() : List.copyOf(friendIds);
        cards = cards == null ? List.of() : List.copyOf(cards);
    }
}
