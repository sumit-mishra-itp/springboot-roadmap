package dev.roadmap.rest.book;

import java.util.List;
import java.util.function.Function;

/** Offset pagination envelope. Without Spring Data there is no Page/Pageable, and a record is all it takes. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(content.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }
}
