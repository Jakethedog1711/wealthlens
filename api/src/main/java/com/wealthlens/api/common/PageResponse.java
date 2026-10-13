package com.wealthlens.api.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Our own, stable JSON shape for paged results. Returning Spring's Page object directly
 * would expose internal fields that can change between Spring versions.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(),
                p.getTotalElements(), p.getTotalPages());
    }
}
