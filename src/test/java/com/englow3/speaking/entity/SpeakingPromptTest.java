package com.englow3.speaking.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

/**
 * A prompt is the fourth content type to run the review workflow, and the first outside {@code learning}. The other
 * three are covered together in {@code ContentReviewLifecycleTest}; this file is the speaking half of the same question
 * - whether the workflow still means the same thing here - kept separate because a learning-module test importing a
 * speaking entity would be the boundary the module map exists to prevent.
 */
class SpeakingPromptTest {

    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();

    private static SpeakingPrompt draft() {
        return SpeakingPrompt.draft("seat-sit", "Seat vs sit", "Minimal Pairs", "A2", "Please sit on this seat.", null,
                null, "/iː/ vs /ɪ/", "[]", AUTHOR_ID);
    }

    private static SpeakingPrompt pending() {
        SpeakingPrompt prompt = draft();
        prompt.submitForReview(Instant.now());
        return prompt;
    }

    @Nested
    class Authoring {

        /** The sentence to be said is the whole prompt - there is no draft worth keeping without one. */
        @Test
        void refusesAPromptWithNothingToSay() {
            assertThatThrownBy(() -> SpeakingPrompt.draft("empty", "Empty", "Minimal Pairs", "A2", "   ", null, null,
                    null, "[]", AUTHOR_ID)).isInstanceOf(BadRequestException.class)
                            .extracting(e -> ((BadRequestException) e).getCode())
                            .isEqualTo("SPEAKING_PROMPT_NO_REFERENCE_TEXT");
        }

        /** Stored stripped, because it is read aloud and compared word for word against what comes back. */
        @Test
        void keepsTheReferenceTextWithoutItsSurroundingSpace() {
            SpeakingPrompt prompt = SpeakingPrompt.draft("s", "S", "Minimal Pairs", "A2", "  Please sit.  ", null, null,
                    null, "[]", AUTHOR_ID);

            assertThat(prompt.getReferenceText()).isEqualTo("Please sit.");
        }

        /**
         * Tips are read straight into a JSON response, where a null would arrive as a broken field, not an empty one.
         */
        @Test
        void standsInAnEmptyTipListRatherThanNothingAtAll() {
            SpeakingPrompt prompt = SpeakingPrompt.draft("s", "S", "Minimal Pairs", "A2", "Please sit.", null, null,
                    null, null, AUTHOR_ID);

            assertThat(prompt.getTips()).isEqualTo("[]");
        }

        @Test
        void startsUnpublished() {
            assertThat(draft().getStatus()).isEqualTo(SpeakingPromptStatus.DRAFT);
            assertThat(draft().getPublishedAt()).isNull();
        }
    }

    @Nested
    class Review {

        @Test
        void sendsADraftToTheQueue() {
            SpeakingPrompt prompt = draft();
            Instant submittedAt = Instant.now();

            prompt.submitForReview(submittedAt);

            assertThat(prompt.getStatus()).isEqualTo(SpeakingPromptStatus.PENDING_REVIEW);
            assertThat(prompt.getSubmittedForReviewAt()).isEqualTo(submittedAt);
            assertThat(prompt.getPublishedAt()).isNull();
        }

        /**
         * Approval publishes in the same step, as it does for the other three: approved-but-unpublished is not a state.
         */
        @Test
        void publishesOnApprovalAndRecordsWhoDecided() {
            SpeakingPrompt prompt = pending();
            Instant approvedAt = Instant.now();

            prompt.approve(REVIEWER_ID, approvedAt);

            assertThat(prompt.getStatus()).isEqualTo(SpeakingPromptStatus.PUBLISHED);
            assertThat(prompt.getPublishedAt()).isEqualTo(approvedAt);
            assertThat(prompt.getReviewedByUserId()).isEqualTo(REVIEWER_ID);
        }

        @Test
        void refusesToApproveAPromptNobodySubmitted() {
            assertThatThrownBy(() -> draft().approve(REVIEWER_ID, Instant.now())).isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("SPEAKING_PROMPT_NOT_PENDING_REVIEW");
        }

        @Test
        void keepsTheReasonWhenTurningAPromptBack() {
            SpeakingPrompt prompt = pending();

            prompt.reject(REVIEWER_ID, "  The IPA does not match the sentence.  ", Instant.now());

            assertThat(prompt.getStatus()).isEqualTo(SpeakingPromptStatus.REJECTED);
            assertThat(prompt.getReviewNote()).isEqualTo("The IPA does not match the sentence.");
        }

        @Test
        void letsARejectedPromptBeResubmitted() {
            SpeakingPrompt prompt = pending();
            prompt.reject(REVIEWER_ID, "The IPA does not match.", Instant.now());

            prompt.submitForReview(Instant.now());

            assertThat(prompt.getStatus()).isEqualTo(SpeakingPromptStatus.PENDING_REVIEW);
        }

        /** The same rule and the same code as the other three types, so a client maps one thing. */
        @Test
        void refusesARejectionWithNoReason() {
            assertThatThrownBy(() -> pending().reject(REVIEWER_ID, "  ", Instant.now()))
                    .isInstanceOf(BadRequestException.class).extracting(e -> ((BadRequestException) e).getCode())
                    .isEqualTo("REVIEW_NOTE_REQUIRED");
        }

        /**
         * A refused rejection must not half-apply: the prompt stays in the queue rather than landing in REJECTED with
         * nothing to explain it.
         */
        @Test
        void leavesAPromptInTheQueueWhenTheReasonIsMissing() {
            SpeakingPrompt prompt = pending();

            assertThatThrownBy(() -> prompt.reject(REVIEWER_ID, null, Instant.now()))
                    .isInstanceOf(BadRequestException.class);

            assertThat(prompt.getStatus()).isEqualTo(SpeakingPromptStatus.PENDING_REVIEW);
        }

        /** A note from an earlier round describes a prompt that no longer exists, so approval clears it. */
        @Test
        void dropsAStaleNoteOnApproval() {
            SpeakingPrompt prompt = pending();
            prompt.reject(REVIEWER_ID, "The IPA does not match.", Instant.now());
            prompt.submitForReview(Instant.now());

            prompt.approve(REVIEWER_ID, Instant.now());

            assertThat(prompt.getReviewNote()).isNull();
        }
    }

    @Nested
    class PublishingAndRetiring {

        /**
         * The shortcut an administrator holding the approval power would otherwise take by approving their own work.
         */
        @Test
        void publishesADraftDirectly() {
            SpeakingPrompt prompt = draft();
            Instant publishedAt = Instant.now();

            prompt.publish(publishedAt);

            assertThat(prompt.getStatus()).isEqualTo(SpeakingPromptStatus.PUBLISHED);
            assertThat(prompt.getPublishedAt()).isEqualTo(publishedAt);
        }

        /**
         * A prompt already with a reviewer cannot be published a second way. Without this, the direct publish would be
         * a route around a rejection - reject, then publish, and the note stands over a live prompt.
         */
        @Test
        void refusesToPublishAnythingThatIsNotADraft() {
            SpeakingPrompt rejected = pending();
            rejected.reject(REVIEWER_ID, "The IPA does not match.", Instant.now());

            assertThatThrownBy(() -> rejected.publish(Instant.now())).isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("SPEAKING_PROMPT_NOT_DRAFT");
            assertThatThrownBy(() -> pending().publish(Instant.now())).isInstanceOf(ConflictException.class);
        }

        @Test
        void retiresAPromptWaitingOnReview() {
            SpeakingPrompt prompt = pending();

            prompt.archive();

            assertThat(prompt.getStatus()).isEqualTo(SpeakingPromptStatus.ARCHIVED);
        }

        @Test
        void refusesToArchiveTwice() {
            SpeakingPrompt prompt = draft();
            prompt.archive();

            assertThatThrownBy(prompt::archive).isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("SPEAKING_PROMPT_ALREADY_ARCHIVED");
        }

        /** Archiving is the end of the line: a retired prompt cannot be quietly put back into the queue. */
        @Test
        void refusesToResubmitAnArchivedPrompt() {
            SpeakingPrompt prompt = draft();
            prompt.archive();

            assertThatThrownBy(() -> prompt.submitForReview(Instant.now())).isInstanceOf(ConflictException.class)
                    .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("SPEAKING_PROMPT_NOT_SUBMITTABLE");
        }
    }
}
