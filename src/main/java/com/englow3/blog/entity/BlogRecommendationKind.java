package com.englow3.blog.entity;

/**
 * Why a post is recommended: it fits what the reader declared, it is what readers liked this week, or - when neither
 * fills the list - it is simply one of the newest.
 */
public enum BlogRecommendationKind {
    PROFILE, POPULAR_THIS_WEEK, LATEST
}
