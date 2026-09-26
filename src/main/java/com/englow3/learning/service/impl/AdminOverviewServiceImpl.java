package com.englow3.learning.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.learning.dto.result.AdminOverviewResult;
import com.englow3.learning.dto.result.AdminOverviewResult.ContentKindCounts;
import com.englow3.learning.query.AdminOverviewQuery;

import lombok.RequiredArgsConstructor;
import com.englow3.learning.service.*;

/** The administrator's landing page: what needs a decision, and whether the product is being used. */
@Service
@RequiredArgsConstructor
public class AdminOverviewServiceImpl implements AdminOverviewService {

    /** A week: long enough to smooth a quiet day, short enough to notice a quiet week. */
    static final int PERIOD_DAYS = 7;

    private final AdminOverviewQuery overviewQuery;

    @Transactional(readOnly = true)
    public AdminOverviewResult overview() {
        List<ContentKindCounts> content = overviewQuery.contentCounts().stream()
                .map(row -> new ContentKindCounts(row.kind(), row.drafts(), row.pendingReview(), row.published()))
                .toList();
        long pending = content.stream().mapToLong(ContentKindCounts::pendingReview).sum();

        AdminOverviewQuery.Activity activity = overviewQuery
                .activitySince(Instant.now().minus(PERIOD_DAYS, ChronoUnit.DAYS));

        return new AdminOverviewResult(content, pending, activity.learners(), activity.newLearners(),
                activity.activeLearners(), activity.cardReviews(), activity.dictationSentences(),
                activity.quizzesSubmitted(), activity.examsSubmitted(), PERIOD_DAYS);
    }
}
