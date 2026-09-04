package com.englow3.shared.page;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Spring Data's own {@code Page} serialises to an unstable shape and warns about it, so the wire format is pinned here
 * instead. Not a success envelope - the HTTP status still carries that.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
