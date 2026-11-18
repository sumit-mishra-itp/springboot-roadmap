package dev.roadmap.rest.book;

import java.math.BigDecimal;

/** API v2 representation: price became an object. That is a breaking change, hence a new version. */
public record BookResponseV2(long id, String title, String author, String isbn, Money price) {

    public record Money(BigDecimal amount, String currency) {
    }

    static BookResponseV2 from(Book book) {
        // ponytail: single-currency store, add a currency field to Book when a second market appears
        return new BookResponseV2(book.id(), book.title(), book.author(), book.isbn(), new Money(book.price(), "USD"));
    }
}
