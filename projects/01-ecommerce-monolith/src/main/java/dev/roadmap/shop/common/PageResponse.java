package dev.roadmap.shop.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Stable JSON envelope for every collection endpoint. Serializing Spring Data's PageImpl directly leaks its
 * internal shape (pageable, sort, etc.), and that shape is not a contract Spring Data promises to keep stable.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
