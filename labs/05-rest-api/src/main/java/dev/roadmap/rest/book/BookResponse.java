package dev.roadmap.rest.book;

import java.math.BigDecimal;

/** API v1 representation: price is a bare number. */
public record BookResponse(long id, String title, String author, String isbn, BigDecimal price) {

    static BookResponse from(Book book) {
        return new BookResponse(book.id(), book.title(), book.author(), book.isbn(), book.price());
    }
}
