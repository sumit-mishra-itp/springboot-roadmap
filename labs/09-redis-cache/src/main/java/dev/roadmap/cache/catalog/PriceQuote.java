package dev.roadmap.cache.catalog;

/** Price of a product converted to another currency. Short-lived, so its cache has a short TTL. */
public record PriceQuote(long productId, String currency, long amountMinor) {
}
