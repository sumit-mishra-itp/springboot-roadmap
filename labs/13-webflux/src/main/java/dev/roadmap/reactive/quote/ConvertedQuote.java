package dev.roadmap.reactive.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ConvertedQuote(String symbol, String currency, BigDecimal price, boolean stale) {

    static ConvertedQuote of(Quote quote, String currency, BigDecimal usdRate) {
        return new ConvertedQuote(quote.symbol(), currency,
                quote.price().multiply(usdRate).setScale(4, RoundingMode.HALF_UP), quote.stale());
    }
}
