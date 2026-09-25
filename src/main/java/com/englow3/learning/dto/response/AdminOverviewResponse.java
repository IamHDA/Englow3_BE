package com.englow3.learning.dto.response;

import java.util.List;

import com.englow3.learning.dto.result.AdminOverviewResult;

public record AdminOverviewResponse(List<ContentKindCounts> content, long pendingReviewTotal, long learners,
        long newLearners, long activeLearners, long cardReviews, long dictationSentences, long quizzesSubmitted,
        long examsSubmitted, int periodDays) {

    public record ContentKindCounts(String kind, long drafts, long pendingReview, long published) {
    }

    public static AdminOverviewResponse from(AdminOverviewResult result) {
        return new AdminOverviewResponse(
                result.content().stream().map(row -> new ContentKindCounts(row.kind(), row.drafts(),
                        row.pendingReview(), row.published())).toList(),
                result.pendingReviewTotal(), result.learners(), result.newLearners(), result.activeLearners(),
                result.cardReviews(), result.dictationSentences(), result.quizzesSubmitted(), result.examsSubmitted(),
                result.periodDays());
    }
}
