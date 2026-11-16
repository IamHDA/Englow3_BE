package com.englow3.blog.dto.result;

import java.util.Comparator;

import com.englow3.blog.entity.BlogTagKind;

public record BlogTagResult(BlogTagKind kind, String value) {

    /** Skills, then levels, then certificates; alphabetical within a kind, so a post always lists its tags alike. */
    public static final Comparator<BlogTagResult> DISPLAY_ORDER = Comparator.comparing(BlogTagResult::kind)
            .thenComparing(BlogTagResult::value);
}
