package com.englow3.flashcard.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.flashcard.entity.FlashcardSet;
import com.englow3.flashcard.entity.FlashcardSetKind;
import com.englow3.flashcard.entity.FlashcardSetVisibility;

/**
 * A set as it appears in the catalogue, with the three numbers that are per-learner rather than per-set. They are
 * carried here instead of on the entity because two learners looking at the same set see different ones.
 * <p>
 * {@code ownerId} and {@code owner} are set on a learner's own (personal) set only; a catalogue set has an author, not
 * an owner, and its author is not shown to learners. {@code owner} is filled in by the service, which asks the user
 * module for a whole page of names at once.
 */
public record FlashcardSetSummaryResult(UUID id, String slug, String name, String description, String topic,
        String targetLevel, long cardCount, long dueCount, long masteredCount, Instant lastStudiedAt,
        FlashcardSetKind kind, FlashcardSetVisibility visibility, UUID ownerId, Owner owner, boolean canEdit) {

    public record Owner(UUID id, String displayName, String avatarUrl) {
    }

    /** {@code viewerId} decides {@code canEdit}: only the owner of a personal set edits it from the learner side. */
    public static FlashcardSetSummaryResult of(FlashcardSet set, UUID viewerId, long cardCount, long dueCount,
            long masteredCount, Instant lastStudiedAt) {
        return new FlashcardSetSummaryResult(set.getId(), set.getSlug(), set.getName(), set.getDescription(),
                set.getTopic(), set.getTargetLevel(), cardCount, dueCount, masteredCount, lastStudiedAt, set.getKind(),
                set.getVisibility(), set.isPersonal() ? set.getCreatedByUserId() : null, null,
                viewerId != null && set.isOwnedBy(viewerId));
    }

    public FlashcardSetSummaryResult withOwner(Owner owner) {
        return new FlashcardSetSummaryResult(id, slug, name, description, topic, targetLevel, cardCount, dueCount,
                masteredCount, lastStudiedAt, kind, visibility, ownerId, owner, canEdit);
    }
}
