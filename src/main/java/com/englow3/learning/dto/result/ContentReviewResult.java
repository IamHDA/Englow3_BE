package com.englow3.learning.dto.result;

import java.time.Instant;
import java.util.UUID;

import com.englow3.learning.entity.DictationLesson;
import com.englow3.learning.entity.FlashcardSet;
import com.englow3.learning.entity.Quiz;

/**
 * What an authoring action returns: where the content now stands, and the review trail behind it.
 * <p>
 * Its own shape rather than the catalogue summary the learner screens use, for the same reason a sat paper and a marked
 * paper are different types: this one carries the review note, and a learner must never read "rejected because the
 * audio is unusable". Keeping them separate means there is no field here to forget to clear.
 * <p>
 * {@code status} is a String, and that is the one compromise in this record. The three content types keep separate
 * status enums on purpose, so a record shared by all three cannot be typed to one of them. The values are identical by
 * construction and the transport layer validates them against an enum of its own; three identical records and three
 * identical GraphQL types would be the alternative.
 *
 * @param itemCount
 *            cards, questions or sentences - whatever the type is made of
 */
public record ContentReviewResult(UUID id, String slug, String title, String status, long itemCount,
        Instant publishedAt, Instant submittedForReviewAt, UUID reviewedByUserId, Instant reviewedAt,
        String reviewNote) {

    public static ContentReviewResult of(FlashcardSet set, long cardCount) {
        return new ContentReviewResult(set.getId(), set.getSlug(), set.getName(), set.getStatus().name(), cardCount,
                set.getPublishedAt(), set.getReview().getSubmittedForReviewAt(), set.getReview().getReviewedByUserId(),
                set.getReview().getReviewedAt(), set.getReview().getReviewNote());
    }

    public static ContentReviewResult of(Quiz quiz, long questionCount) {
        return new ContentReviewResult(quiz.getId(), quiz.getSlug(), quiz.getTitle(), quiz.getStatus().name(),
                questionCount, quiz.getPublishedAt(), quiz.getReview().getSubmittedForReviewAt(),
                quiz.getReview().getReviewedByUserId(), quiz.getReview().getReviewedAt(),
                quiz.getReview().getReviewNote());
    }

    public static ContentReviewResult of(DictationLesson lesson, long sentenceCount) {
        return new ContentReviewResult(lesson.getId(), lesson.getSlug(), lesson.getTitle(), lesson.getStatus().name(),
                sentenceCount, lesson.getPublishedAt(), lesson.getReview().getSubmittedForReviewAt(),
                lesson.getReview().getReviewedByUserId(), lesson.getReview().getReviewedAt(),
                lesson.getReview().getReviewNote());
    }
}
