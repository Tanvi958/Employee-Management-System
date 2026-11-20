package com.example.Employee_Management.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable JSON shape for paged results (Spring's own Page serialisation is not guaranteed to stay the same). */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
