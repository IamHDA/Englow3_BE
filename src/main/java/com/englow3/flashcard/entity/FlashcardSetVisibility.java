package com.englow3.flashcard.entity;

/**
 * Who besides its owner may see a personal set. Always PUBLIC on a catalogue set, where the review status is what
 * decides visibility instead.
 * <p>
 * SELECTED_FRIENDS is a list chosen by the owner, not "all my friends": each reader must be on the list and still be
 * the owner's friend.
 */
public enum FlashcardSetVisibility {
    PRIVATE, PUBLIC, SELECTED_FRIENDS
}
