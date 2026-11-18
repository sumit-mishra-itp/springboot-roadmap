package dev.roadmap.reactive.price;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceResponse(Long id, String symbol, BigDecimal amount, Instant recordedAt) {

    static PriceResponse from(Price price) {
        return new PriceResponse(price.id(), price.symbol(), price.amount(), price.recordedAt());
    }
}
