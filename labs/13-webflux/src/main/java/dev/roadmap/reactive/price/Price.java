package dev.roadmap.reactive.price;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One row of the {@code price} table. Spring Data R2DBC maps it through the record's canonical constructor.
 * There is no persistence context: no lazy loading, no dirty checking, no cascades. A save is one INSERT or UPDATE.
 */
@Table("price")
public record Price(@Id Long id, String symbol, BigDecimal amount, Instant recordedAt) {

    static Price newPrice(String symbol, BigDecimal amount) {
        // A null id means INSERT. The generated id comes back in a NEW record instance (records are immutable).
        return new Price(null, symbol, amount, Instant.now());
    }
}
