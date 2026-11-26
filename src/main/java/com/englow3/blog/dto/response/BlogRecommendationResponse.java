package com.englow3.blog.dto.response;

import java.util.List;

import com.englow3.blog.dto.result.BlogRecommendationResult;

/** A post and the reason it is recommended: {@code kind} says which kind of reason, the tags say what matched. */
public record BlogRecommendationResponse(BlogPostSummaryResponse post, Reason reason) {

    /** {@code kind} is PROFILE, POPULAR_THIS_WEEK or LATEST; {@code matchedTags} is empty for the last two. */
    public record Reason(String kind, List<BlogTagResponse> matchedTags) {
    }

    public static BlogRecommendationResponse from(BlogRecommendationResult result) {
        return new BlogRecommendationResponse(BlogPostSummaryResponse.from(result.post()),
                new Reason(result.kind().name(), result.matchedTags().stream().map(BlogTagResponse::from).toList()));
    }
}
