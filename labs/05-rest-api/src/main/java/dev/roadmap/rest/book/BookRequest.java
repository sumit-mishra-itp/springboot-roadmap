package dev.roadmap.rest.book;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for POST and PUT. Records give equals/hashCode for free, and the idempotency
 * store uses that as the request "fingerprint" (same key + different body = 422).
 */
public record BookRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String author,
        @NotNull @Isbn String isbn,
        @NotNull @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal price) {

    Book toBook(long id, long version) {
        return new Book(id, title, author, isbn, price, version);
    }
}
