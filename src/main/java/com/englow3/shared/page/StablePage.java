package com.englow3.shared.page;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;

/**
 * Makes a page's order repeatable.
 * <p>
 * A list sorted on one column - creation time, a title - has no defined order among rows that tie, and a database is
 * free to return tied rows differently on the next call. Page two of such a list can then repeat a row from page one
 * and skip another, and a batch import (every row created in the same instant) ties on <em>all</em> of them. Ending the
 * sort with the primary key removes the ambiguity; ending it with something a reader would expect (a title, a number)
 * first makes the tie resolve the way they would.
 */
public final class StablePage {

    private StablePage() {
    }

    /** The requested sort, then each of {@code thenBy}, then the id. An unsorted request stays unsorted. */
    public static Pageable of(Pageable requested, Sort... thenBy) {
        if (requested.isUnpaged() || requested.getSort().isUnsorted()) {
            return requested;
        }
        Sort sort = requested.getSort();
        for (Sort next : thenBy) {
            sort = sort.and(next);
        }
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), sort);
    }

    /**
     * Titles with a number at the end in the order a person counts: "Test 2" before "Test 10". Plain text order puts
     * "Test 10" first because "1" sorts before "2". The title is split into the text before the trailing number and the
     * number itself, which covers a numbered series of papers; a title with no trailing number sorts by its text.
     *
     * @param alias
     *            the entity alias in the query this sort is applied to ({@code e} in {@code select e from Exam e})
     */
    public static Sort byTitleNaturally(String alias, Sort.Direction direction) {
        String title = alias + ".title";
        // The text before the trailing number, then that number as a number. regexp_replace for both halves: HQL
        // rewrites function('substring', ...) into the two-argument substr(text, int), which takes no pattern.
        return JpaSort.unsafe(direction, "function('regexp_replace', " + title + ", '[0-9]+$', '')").and(JpaSort.unsafe(
                direction, "cast(nullif(function('regexp_replace', " + title + ", '^.*[^0-9]', ''), '') as long)"));
    }
}
