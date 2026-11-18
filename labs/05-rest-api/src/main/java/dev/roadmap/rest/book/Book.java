package dev.roadmap.rest.book;

import java.math.BigDecimal;

/** Domain object. Never serialized directly: the API speaks {@link BookResponse} / {@link BookResponseV2}. */
public record Book(long id, String title, String author, String isbn, BigDecimal price, long version) {
}
