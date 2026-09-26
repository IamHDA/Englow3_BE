package com.englow3.progress.service.impl;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.progress.dto.result.AdminOverviewResult;
import com.englow3.progress.dto.result.AdminOverviewResult.ContentKindCounts;
import com.englow3.progress.query.AdminOverviewQuery;
import com.englow3.progress.service.AdminOverviewService;

import lombok.RequiredArgsConstructor;

/** The administrator's landing page: what needs a decision, and whether the product is being used. */
@Service
@RequiredArgsConstructor
public class AdminOverviewServiceImpl implements AdminOverviewService {

    /** A week: long enough to smooth a quiet day, short enough to notice a quiet week. */
    static final int PERIOD_DAYS = 7;

    private final AdminOverviewQuery overviewQuery;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminOverviewResult overview() {
        List<ContentKindCounts> content = overviewQuery.contentCounts().stream()
                .map(row -> new ContentKindCounts(row.kind(), row.drafts(), row.pendingReview(), row.published()))
                .toList();
        long pending = content.stream().mapToLong(ContentKindCounts::pendingReview).sum();

        AdminOverviewQuery.Activity activity = overviewQuery
                .activitySince(clock.instant().minus(PERIOD_DAYS, ChronoUnit.DAYS));

        return new AdminOverviewResult(content, pending, activity.learners(), activity.newLearners(),
                activity.activeLearners(), activity.cardReviews(), activity.dictationSentences(),
                activity.quizzesSubmitted(), activity.examsSubmitted(), PERIOD_DAYS);
    }
}
