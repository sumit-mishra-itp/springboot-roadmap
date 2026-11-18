package dev.roadmap.reactive.quote;

import java.math.BigDecimal;

/** What the downstream returns. {@code stale} is true only when we served a cached copy as a fallback. */
public record Quote(String symbol, BigDecimal price, boolean stale) {

    Quote asStale() {
        return new Quote(symbol, price, true);
    }
}
