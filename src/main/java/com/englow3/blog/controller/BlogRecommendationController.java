package com.englow3.blog.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.blog.dto.response.BlogRecommendationResponse;
import com.englow3.blog.service.BlogRecommendationService;

/** Posts recommended to the caller. Needs a signed-in account, like every route of the blog. */
@RestController
@RequestMapping("/api/blog")
public class BlogRecommendationController {

    private final BlogRecommendationService recommendationService;

    public BlogRecommendationController(BlogRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /** At most {@code limit} posts (3 unless asked, never more than 10), the best first, each with its reason. */
    @GetMapping("/recommendations")
    public ResponseEntity<List<BlogRecommendationResponse>> recommendations(
            @RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity
                .ok(recommendationService.recommend(limit).stream().map(BlogRecommendationResponse::from).toList());
    }
}
