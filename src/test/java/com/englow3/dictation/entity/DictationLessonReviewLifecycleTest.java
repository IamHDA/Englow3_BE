package com.englow3.dictation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;

class DictationLessonReviewLifecycleTest {

    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final UUID REVIEWER_ID = UUID.randomUUID();

    @Test
    void sendsADraftToTheQueue() {
        DictationLesson lesson = draftLesson();

        lesson.submitForReview(8, Instant.now());

        assertThat(lesson.getStatus()).isEqualTo(DictationLessonStatus.PENDING_REVIEW);
    }

    @Test
    void refusesToSubmitAnEmptyLesson() {
        assertThatThrownBy(() -> draftLesson().submitForReview(0, Instant.now())).isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("DICTATION_LESSON_EMPTY");
    }

    @Test
    void publishesOnApproval() {
        DictationLesson lesson = pendingLesson();

        lesson.approve(REVIEWER_ID, 8, Instant.now());

        assertThat(lesson.getStatus()).isEqualTo(DictationLessonStatus.PUBLISHED);
    }

    @Test
    void retiresALessonWaitingOnReview() {
        DictationLesson lesson = pendingLesson();

        lesson.archive();

        assertThat(lesson.getStatus()).isEqualTo(DictationLessonStatus.ARCHIVED);
    }

    private static DictationLesson draftLesson() {
        return DictationLesson.draft("airport", "At the airport", "travel", "A2", AUTHOR_ID);
    }

    private static DictationLesson pendingLesson() {
        DictationLesson lesson = draftLesson();
        lesson.submitForReview(8, Instant.now());
        return lesson;
    }

    @Test
    void refusesARejectionWithNoReasonWithoutChangingStatus() {
        DictationLesson lesson = pendingLesson();

        assertThatThrownBy(() -> lesson.reject(REVIEWER_ID, "", Instant.now())).isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getCode()).isEqualTo("REVIEW_NOTE_REQUIRED");
        assertThat(lesson.getStatus()).isEqualTo(DictationLessonStatus.PENDING_REVIEW);
    }
}
