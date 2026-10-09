package com.englow3.exam.helper;

import org.springframework.data.domain.Sort;

import com.englow3.shared.page.StablePage;

/**
 * The orders a learner can ask the exam catalogue for.
 * <p>
 * Applied by the database, before the page is cut: the screen shows eight papers at a time, and ordering only those
 * eight in the browser meant "by level" or "by score" sorted one page and left the rest where they were. Every order
 * ends with the paper's title read the way a person counts ("Test 2" before "Test 10"), so papers that tie - a batch
 * created in the same instant - still come out as a numbered series rather than in whatever order the database liked.
 */
public enum ExamCatalogueSort {

    NEWEST, LEVEL_ASC, LEVEL_DESC, SCORE_DESC;

    /** Sorts on the {@code Exam} entity as aliased {@code e} in the catalogue query. */
    public Sort toSort() {
        Sort byTitle = StablePage.byTitleNaturally("e", Sort.Direction.ASC);
        return switch (this) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt").and(byTitle);
            case LEVEL_ASC -> Sort.by(Sort.Direction.ASC, "targetLevel").and(byTitle);
            case LEVEL_DESC -> Sort.by(Sort.Direction.DESC, "targetLevel").and(byTitle);
            case SCORE_DESC -> Sort.by(Sort.Direction.DESC, "maxRawScore").and(byTitle);
        };
    }
}
