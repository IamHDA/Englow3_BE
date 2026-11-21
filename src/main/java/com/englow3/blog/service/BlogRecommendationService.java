package com.englow3.blog.service;

import java.util.List;

import com.englow3.blog.dto.result.BlogRecommendationResult;

/**
 * Posts worth the caller's time, with the reason for each. The same answer for the same data: nothing is rotated or
 * drawn at random.
 */
public interface BlogRecommendationService {

    /**
     * The best {@code limit} posts for the caller, at most 10. Posts that fit what the caller declared about their
     * learning come first, best fit first; when none fits enough - or the caller has declared nothing, as a Staff or
     * Admin account has not - it is the posts most liked in the past week; when there are none of those, nothing. Never
     * the caller's own posts, ones they saved, ones they cannot read.
     */
    List<BlogRecommendationResult> recommend(int limit);
}
