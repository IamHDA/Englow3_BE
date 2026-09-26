package com.englow3.flashcard.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

class FlashcardSetReviewLifecycleTest {

    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();

    @Test
    void sendsADraftToTheQueue() {
        FlashcardSet set = draftSet();
        Instant submittedAt = Instant.now();

        set.submitForReview(20, submittedAt);

        assertThat(set.getStatus()).isEqualTo(FlashcardSetStatus.PENDING_REVIEW);
        assertThat(set.getReview().getSubmittedForReviewAt()).isEqualTo(submittedAt);
        assertThat(set.getPublishedAt()).isNull();
    }

    /** A reviewer opening an empty set learns nothing they can act on, and the author is who can fill it. */
    @Test
    void refusesToSubmitAnEmptySet() {
        assertThatThrownBy(() -> draftSet().submitForReview(0, Instant.now())).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("FLASHCARD_SET_EMPTY");
    }

    @Test
    void publishesOnApprovalAndRecordsWhoDecided() {
        FlashcardSet set = pendingSet();
        Instant approvedAt = Instant.now();

        set.approve(REVIEWER_ID, 20, approvedAt);

        assertThat(set.getStatus()).isEqualTo(FlashcardSetStatus.PUBLISHED);
        assertThat(set.getPublishedAt()).isEqualTo(approvedAt);
        assertThat(set.getReview().getReviewedByUserId()).isEqualTo(REVIEWER_ID);
    }

    @Test
    void refusesToApproveASetNobodySubmitted() {
        assertThatThrownBy(() -> draftSet().approve(REVIEWER_ID, 20, Instant.now()))
                .isInstanceOf(ConflictException.class).extracting(e -> ((ConflictException) e).getCode())
                .isEqualTo("FLASHCARD_SET_NOT_PENDING_REVIEW");
    }

    @Test
    void keepsTheReasonWhenTurningASetBack() {
        FlashcardSet set = pendingSet();

        set.reject(REVIEWER_ID, "  Twelve cards have no audio.  ", Instant.now());

        assertThat(set.getStatus()).isEqualTo(FlashcardSetStatus.REJECTED);
        assertThat(set.getReview().getReviewNote()).isEqualTo("Twelve cards have no audio.");
    }

    @Test
    void letsARejectedSetBeResubmitted() {
        FlashcardSet set = pendingSet();
        set.reject(REVIEWER_ID, "Twelve cards have no audio.", Instant.now());

        set.submitForReview(20, Instant.now());

        assertThat(set.getStatus()).isEqualTo(FlashcardSetStatus.PENDING_REVIEW);
    }

    private static FlashcardSet draftSet() {
        return FlashcardSet.draft("core-500", "Core 500", "", "general", "B1", AUTHOR_ID);
    }

    private static FlashcardSet pendingSet() {
        FlashcardSet set = draftSet();
        set.submitForReview(20, Instant.now());
        return set;
    }

    @Test
    void refusesARejectionWithNoReasonWithoutChangingStatus() {
        FlashcardSet set = pendingSet();

        assertThatThrownBy(() -> set.reject(REVIEWER_ID, "  ", Instant.now())).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("REVIEW_NOTE_REQUIRED");
        assertThat(set.getStatus()).isEqualTo(FlashcardSetStatus.PENDING_REVIEW);
    }
}
