package com.englow3.blog.dto.result;

import java.util.List;

/** The values a tag may take, for a filter or a tag picker. */
public record BlogTagCatalogue(List<String> skills, List<String> levels, List<String> certificates) {
}
