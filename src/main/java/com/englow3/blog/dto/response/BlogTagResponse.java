package com.englow3.blog.dto.response;

import com.englow3.blog.dto.result.BlogTagResult;

public record BlogTagResponse(String kind, String value) {

    static BlogTagResponse from(BlogTagResult result) {
        return new BlogTagResponse(result.kind().name(), result.value());
    }
}
