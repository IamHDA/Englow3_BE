package com.englow3.blog.dto.result;

import java.util.List;

import com.englow3.blog.entity.BlogRecommendationKind;

/**
 * A recommended post and why. {@code matchedTags} are the tags of the post that are what the reader declared - empty
 * for {@link BlogRecommendationKind#POPULAR_THIS_WEEK} and {@link BlogRecommendationKind#LATEST}, which are not about
 * the reader at all.
 */
public record BlogRecommendationResult(BlogPostSummaryResult post, BlogRecommendationKind kind,
        List<BlogTagResult> matchedTags) {
}
