package dev.roadmap.rest.book;

import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/** Keyset (cursor) pagination envelope. nextCursor is null on the last page. */
public record CursorPage<T>(List<T> items, @Nullable String nextCursor) {

    <R> CursorPage<R> map(Function<T, R> mapper) {
        return new CursorPage<>(items.stream().map(mapper).toList(), nextCursor);
    }
}
