package com.englow3.flashcard.entity;

import java.time.Instant;
import java.util.UUID;

import com.englow3.flashcard.dto.command.CreateFlashcardSetCommand;
import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.ForbiddenException;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "flashcard_sets")
@Getter
public class FlashcardSet {

    @jakarta.persistence.Version
    private long version;

    @Column(name = "authoring_updated_at", nullable = false)
    private Instant authoringUpdatedAt = Instant.now();

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String topic;

    @Column(name = "target_level")
    private String targetLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlashcardSetStatus status;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private FlashcardSetKind kind = FlashcardSetKind.CATALOGUE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlashcardSetVisibility visibility = FlashcardSetVisibility.PUBLIC;

    /** Filled by the column default, never by this application - hence not insertable. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Embedded
    private ReviewTrail review = new ReviewTrail();

    /**
     * Never null. Hibernate loads an embeddable whose columns are all null as {@code null}, which overrides the
     * initializer above - so every draft read back before its first review had no trail, and submitting it failed.
     */
    public ReviewTrail getReview() {
        if (review == null) {
            review = new ReviewTrail();
        }
        return review;
    }

    protected FlashcardSet() {
    }

    public static FlashcardSet draft(String slug, String name, String description, String topic, String targetLevel,
            UUID createdByUserId) {
        FlashcardSet set = new FlashcardSet();
        set.id = UUID.randomUUID();
        set.slug = slug;
        set.name = name;
        set.description = description == null ? "" : description;
        set.topic = topic;
        set.targetLevel = targetLevel;
        set.status = FlashcardSetStatus.DRAFT;
        set.createdByUserId = createdByUserId;
        return set;
    }

    /**
     * A learner's own set. It is live from the start - there is no review to wait for, and an empty set is simply one
     * the learner has not filled yet - so it is created published. Its slug is derived from its id: a learner never
     * sees or chooses one, and the column is unique across the whole catalogue.
     */
    public static FlashcardSet personal(String name, String description, String topic, String targetLevel,
            FlashcardSetVisibility visibility, int selectedFriendCount, UUID ownerId, Instant now) {
        requireAudienceMatches(visibility, selectedFriendCount);
        FlashcardSet set = new FlashcardSet();
        set.id = UUID.randomUUID();
        set.slug = "personal-" + set.id;
        set.name = name;
        set.description = description == null ? "" : description;
        set.topic = topic;
        set.targetLevel = targetLevel;
        set.kind = FlashcardSetKind.PERSONAL;
        set.visibility = visibility;
        set.status = FlashcardSetStatus.PUBLISHED;
        set.publishedAt = now;
        set.createdByUserId = ownerId;
        set.authoringUpdatedAt = now;
        return set;
    }

    /**
     * Edits a personal set's details and who may see it. The version the owner loaded is compared, so saving from a
     * second tab cannot silently undo the first one's change of audience.
     */
    public void revisePersonal(String name, String description, String topic, String targetLevel,
            FlashcardSetVisibility visibility, int selectedFriendCount, long expectedVersion, Instant now) {
        requirePersonal();
        if (version != expectedVersion) {
            throw new ConflictException("CONTENT_CHANGED", "This item changed. Reload before saving again");
        }
        requireAudienceMatches(visibility, selectedFriendCount);
        this.name = name;
        this.description = description == null ? "" : description;
        this.topic = topic;
        this.targetLevel = targetLevel;
        this.visibility = visibility;
        // Always dirties the row, so the version moves even when only the friend list changed - that list lives in
        // another table, and a second save racing this one must still be caught.
        this.authoringUpdatedAt = now;
    }

    /**
     * A friend list belongs to SELECTED_FRIENDS and to nothing else: one with nobody on it would be a private set under
     * another name, and a list on a public or private set would be a choice that does nothing.
     */
    private static void requireAudienceMatches(FlashcardSetVisibility visibility, int selectedFriendCount) {
        if (visibility == null) {
            throw new BadRequestException("FLASHCARD_SET_VISIBILITY_REQUIRED", "Choose who can see this set");
        }
        if (visibility == FlashcardSetVisibility.SELECTED_FRIENDS && selectedFriendCount == 0) {
            throw new BadRequestException("FLASHCARD_SET_NO_FRIENDS_SELECTED",
                    "Choose at least one friend who can see this set");
        }
        if (visibility != FlashcardSetVisibility.SELECTED_FRIENDS && selectedFriendCount > 0) {
            throw new BadRequestException("FLASHCARD_SET_FRIENDS_NOT_ALLOWED",
                    "Friends can only be chosen for a set shared with selected friends");
        }
    }

    public boolean isPersonal() {
        return kind == FlashcardSetKind.PERSONAL;
    }

    /** Only a personal set has an owner in this sense; the author of a catalogue set does not own it. */
    public boolean isOwnedBy(UUID userId) {
        return isPersonal() && createdByUserId.equals(userId);
    }

    /**
     * Whether {@code viewerId} may open this set. The two flags are facts the service looked up - they span other
     * tables - and the decision made from them is this set's.
     *
     * @param blockedEitherWay
     *            the owner and the viewer have blocked one another, in either direction
     * @param selectedFriend
     *            the viewer is on this set's friend list and is still the owner's friend
     */
    public boolean isVisibleTo(UUID viewerId, boolean blockedEitherWay, boolean selectedFriend) {
        if (!isPersonal()) {
            return status == FlashcardSetStatus.PUBLISHED;
        }
        if (createdByUserId.equals(viewerId)) {
            return true;
        }
        if (blockedEitherWay) {
            return false;
        }
        return switch (visibility) {
            case PUBLIC -> true;
            case SELECTED_FRIENDS -> selectedFriend;
            case PRIVATE -> false;
        };
    }

    private void requirePersonal() {
        if (!isPersonal()) {
            throw new ConflictException("FLASHCARD_SET_NOT_PERSONAL", "Only a learner's own set can be edited here");
        }
    }

    /**
     * The review lifecycle is the catalogue's. A personal set never enters it, or a learner could publish around it.
     */
    private void requireCatalogue() {
        if (isPersonal()) {
            throw new ConflictException("FLASHCARD_SET_PERSONAL", "A learner's own set has no review lifecycle");
        }
    }

    /**
     * Publishing an empty set would put a set in the catalogue that opens onto nothing, so the count is checked here
     * rather than left to whoever happens to look.
     */
    public void publish(long cardCount, Instant now) {
        requireCatalogue();
        if (status != FlashcardSetStatus.DRAFT) {
            throw new ConflictException("FLASHCARD_SET_NOT_DRAFT",
                    "Only a draft set can be published; this one is %s".formatted(status));
        }
        if (cardCount == 0) {
            throw new ConflictException("FLASHCARD_SET_EMPTY", "A set with no cards cannot be published");
        }
        this.status = FlashcardSetStatus.PUBLISHED;
        this.publishedAt = now;
    }

    /**
     * Hands the set to an administrator. Checked against the publication rule as well, and deliberately at this end: a
     * reviewer opening an empty set learns nothing they can act on, while the author is the one who can fill it.
     */
    public void submitForReview(long cardCount, Instant now) {
        requireCatalogue();
        if (status != FlashcardSetStatus.DRAFT && status != FlashcardSetStatus.REJECTED) {
            throw new ConflictException("FLASHCARD_SET_NOT_SUBMITTABLE",
                    "Only a draft or rejected set can be submitted; this one is %s".formatted(status));
        }
        if (cardCount == 0) {
            throw new ConflictException("FLASHCARD_SET_EMPTY", "A set with no cards cannot be published");
        }
        this.status = FlashcardSetStatus.PENDING_REVIEW;
        getReview().markSubmitted(now);
    }

    /** Approval publishes in the same step: an approved-but-unpublished set would be a state nobody asked for. */
    public void approve(UUID reviewerId, long cardCount, Instant now) {
        requireCatalogue();
        requirePendingReview("approved");
        if (cardCount == 0) {
            throw new ConflictException("FLASHCARD_SET_EMPTY", "A set with no cards cannot be published");
        }
        this.status = FlashcardSetStatus.PUBLISHED;
        this.publishedAt = now;
        getReview().markApproved(reviewerId, now);
    }

    public void reject(UUID reviewerId, String note, Instant now) {
        requireCatalogue();
        requirePendingReview("rejected");
        getReview().markRejected(reviewerId, note, now);
        this.status = FlashcardSetStatus.REJECTED;
    }

    private void requirePendingReview(String verb) {
        if (status != FlashcardSetStatus.PENDING_REVIEW) {
            throw new ConflictException("FLASHCARD_SET_NOT_PENDING_REVIEW",
                    "Only a set waiting on review can be %s; this one is %s".formatted(verb, status));
        }
    }

    /**
     * Whether more cards may be added. A draft or rejected set is still being written. A published one may still grow -
     * a new item is simply unseen by every learner - but only by an administrator: the reviewer is the one who would
     * otherwise have to sign off, so staff adding to a live set would put unreviewed content in front of learners. One
     * under review is frozen, so that what is approved is what was reviewed; an archived one is done.
     */
    public void requireAppendable(boolean callerIsAdministrator) {
        requireCatalogue();
        switch (status) {
            case DRAFT, REJECTED -> {
            }
            case PUBLISHED -> {
                if (!callerIsAdministrator) {
                    throw new ForbiddenException("FLASHCARD_SET_LIVE_ADMIN_ONLY",
                            "Only an administrator can add to a published set");
                }
            }
            case PENDING_REVIEW, ARCHIVED -> throw new ConflictException("FLASHCARD_SET_NOT_EDITABLE",
                    "Nothing can be added to a set that is %s".formatted(status));
        }
    }

    public void updateDraft(CreateFlashcardSetCommand source, long expectedVersion, Instant now) {
        requireCatalogue();
        if ((status != FlashcardSetStatus.DRAFT && status != FlashcardSetStatus.REJECTED) || publishedAt != null) {
            throw new ConflictException("CONTENT_NOT_EDITABLE",
                    "Only an unpublished draft or rejected item can be edited");
        }
        if (version != expectedVersion) {
            throw new ConflictException("CONTENT_CHANGED", "This item changed. Reload before saving again");
        }
        this.slug = source.slug();
        this.name = source.name();
        this.description = source.description();
        this.topic = source.topic();
        this.targetLevel = source.targetLevel();
        authoringUpdatedAt = now;
    }

    public void touchContent() {
        authoringUpdatedAt = Instant.now();
    }

    /**
     * Undoes an archive. Back to where it was: a set that had been published returns to the library as it was -
     * published content is never edited, so it needs no second review - and one that never was goes back to draft.
     * Archiving used to be one-way; a mistaken click could only be undone in the database.
     */
    public void restore() {
        requireCatalogue();
        if (status != FlashcardSetStatus.ARCHIVED) {
            throw new ConflictException("FLASHCARD_SET_NOT_ARCHIVED", "Only an archived set can be restored");
        }
        this.status = publishedAt != null ? FlashcardSetStatus.PUBLISHED : FlashcardSetStatus.DRAFT;
    }

    public void archive() {
        requireCatalogue();
        if (status == FlashcardSetStatus.ARCHIVED) {
            throw new ConflictException("FLASHCARD_SET_ALREADY_ARCHIVED", "This set is already archived");
        }
        this.status = FlashcardSetStatus.ARCHIVED;
    }
}
