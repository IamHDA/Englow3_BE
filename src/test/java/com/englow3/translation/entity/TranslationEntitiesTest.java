package com.englow3.translation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

class TranslationEntitiesTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");

    private static TranslationSet draft() {
        return TranslationSet.draft("daily-life", "Daily life", "daily-life", "A2", null, AUTHOR);
    }

    @Nested
    class Set {

        @Test
        void runsTheReviewWorkflow() {
            TranslationSet set = draft();
            set.submitForReview(3, NOW);
            assertThat(set.getStatus()).isEqualTo(TranslationSetStatus.PENDING_REVIEW);

            set.approve(REVIEWER, NOW);
            assertThat(set.isPublished()).isTrue();
            assertThat(set.getPublishedAt()).isEqualTo(NOW);
            assertThat(set.getReviewedByUserId()).isEqualTo(REVIEWER);
        }

        @Test
        void cannotBeSubmittedOrPublishedWithoutSentences() {
            assertThatThrownBy(() -> draft().submitForReview(0, NOW)).isInstanceOf(ConflictException.class)
                    .hasMessageContaining("at least one sentence");
            assertThatThrownBy(() -> draft().publish(0, NOW)).isInstanceOf(ConflictException.class);
        }

        @Test
        void aRejectionMustSayWhy() {
            TranslationSet set = draft();
            set.submitForReview(1, NOW);
            assertThatThrownBy(() -> set.reject(REVIEWER, " ", NOW)).isInstanceOf(BadRequestException.class);

            set.reject(REVIEWER, "Câu 2 dịch mẫu sai thì.", NOW);
            assertThat(set.getStatus()).isEqualTo(TranslationSetStatus.REJECTED);
            assertThat(set.getReviewNote()).isEqualTo("Câu 2 dịch mẫu sai thì.");
        }

        /** Learners' attempts point at the sentences, so a published set is never rewritten under them. */
        @Test
        void isNotEditableOncePublished() {
            TranslationSet set = draft();
            set.publish(1, NOW);
            assertThatThrownBy(() -> set.updateDraft("x", "x", "x", null, null, set.getVersion(), NOW))
                    .isInstanceOf(ConflictException.class);
        }

        @Test
        void refusesAnEditMadeAgainstAnOldVersion() {
            TranslationSet set = draft();
            assertThatThrownBy(() -> set.updateDraft("x", "x", "x", null, null, set.getVersion() + 1, NOW))
                    .isInstanceOf(ConflictException.class).hasMessageContaining("changed");
        }

        @Test
        void restoresToWhereItWas() {
            TranslationSet published = draft();
            published.publish(1, NOW);
            published.archive();
            published.restore();
            assertThat(published.getStatus()).isEqualTo(TranslationSetStatus.PUBLISHED);

            TranslationSet never = draft();
            never.archive();
            never.restore();
            assertThat(never.getStatus()).isEqualTo(TranslationSetStatus.DRAFT);
        }
    }

    @Nested
    class Item {

        @Test
        void needsBothTheSourceAndAReference() {
            UUID set = UUID.randomUUID();
            assertThatThrownBy(() -> TranslationItem.of(set, 1, " ", "Hello.", null))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> TranslationItem.of(set, 1, "Xin chào.", "", null))
                    .isInstanceOf(BadRequestException.class);
            assertThat(TranslationItem.of(set, 1, " Xin chào. ", "Hello.", " ").getNotes()).isNull();
        }
    }

    @Nested
    class Attempt {

        private TranslationAttempt queued() {
            return TranslationAttempt.queued(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), " Hello. ", NOW);
        }

        @Test
        void refusesAnEmptyOrOverlongAnswer() {
            assertThatThrownBy(() -> TranslationAttempt.queued(AUTHOR, AUTHOR, AUTHOR, "   ", NOW))
                    .isInstanceOf(BadRequestException.class);
            assertThatThrownBy(() -> TranslationAttempt.queued(AUTHOR, AUTHOR, AUTHOR,
                    "a".repeat(TranslationAttempt.MAX_RESPONSE_LENGTH + 1), NOW))
                            .isInstanceOf(BadRequestException.class);
            assertThat(queued().getResponse()).isEqualTo("Hello.");
        }

        /** The grader credited the meaning of a Vietnamese answer; it is refused before it reaches the grader. */
        @Test
        void refusesAnAnswerWrittenInVietnamese() {
            assertThatThrownBy(() -> TranslationAttempt.queued(AUTHOR, AUTHOR, AUTHOR, "Trời mưa to", NOW))
                    .isInstanceOf(BadRequestException.class).hasMessageContaining("English");
            assertThatThrownBy(() -> TranslationAttempt.queued(AUTHOR, AUTHOR, AUTHOR, "toi di hoc đi", NOW))
                    .isInstanceOf(BadRequestException.class);
            // A loanword is not Vietnamese.
            assertThat(TranslationAttempt.queued(AUTHOR, AUTHOR, AUTHOR, "We met at a café.", NOW).getResponse())
                    .isEqualTo("We met at a café.");
        }

        /** A job can be delivered twice; the second grade must not replace the first. */
        @Test
        void keepsItsFirstGrade() {
            TranslationAttempt attempt = queued();
            attempt.recordGrade(80, "{\"score\":80}", NOW);
            attempt.recordGrade(10, "{\"score\":10}", NOW.plusSeconds(5));

            assertThat(attempt.getStatus()).isEqualTo(TranslationAttemptStatus.GRADED);
            assertThat(attempt.getScore()).isEqualTo(80);
        }

        @Test
        void aJobGivingUpLaterDoesNotUndoAGrade() {
            TranslationAttempt attempt = queued();
            attempt.recordGrade(80, "{\"score\":80}", NOW);
            attempt.recordFailure("AI_TIMEOUT", NOW);

            assertThat(attempt.isGraded()).isTrue();
            assertThat(attempt.getErrorCode()).isNull();
        }

        @Test
        void aFailedAttemptIsNotRevivedByALateResult() {
            TranslationAttempt attempt = queued();
            attempt.recordFailure("AI_TIMEOUT", NOW);
            attempt.recordGrade(80, "{\"score\":80}", NOW);

            assertThat(attempt.getStatus()).isEqualTo(TranslationAttemptStatus.FAILED);
            assertThat(attempt.getScore()).isNull();
        }
    }
}
