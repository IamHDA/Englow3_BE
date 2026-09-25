package com.englow3.learning.dto.result;

import java.util.List;

/**
 * What the administrator's overview shows.
 *
 * @param content
 *            one entry per kind of content - flashcard sets, quizzes, dictation lessons, speaking prompts and exams
 * @param periodDays
 *            the window the activity figures cover
 */
public record AdminOverviewResult(List<ContentKindCounts> content, long pendingReviewTotal, long learners,
        long newLearners, long activeLearners, long cardReviews, long dictationSentences, long quizzesSubmitted,
        long examsSubmitted, int periodDays) {

    public record ContentKindCounts(String kind, long drafts, long pendingReview, long published) {
    }
}
