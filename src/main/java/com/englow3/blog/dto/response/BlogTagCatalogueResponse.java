package com.englow3.blog.dto.response;

import java.util.List;

import com.englow3.blog.dto.result.BlogTagCatalogue;

public record BlogTagCatalogueResponse(List<String> skills, List<String> levels, List<String> certificates) {

    public static BlogTagCatalogueResponse from(BlogTagCatalogue catalogue) {
        return new BlogTagCatalogueResponse(catalogue.skills(), catalogue.levels(), catalogue.certificates());
    }
}
