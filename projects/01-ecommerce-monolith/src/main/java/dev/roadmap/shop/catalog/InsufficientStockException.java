package dev.roadmap.shop.catalog;

import org.jspecify.annotations.Nullable;

/** Business-rule failure, not a race: the stock this transaction read is too low. ApiExceptionHandler maps it to 409. */
public class InsufficientStockException extends RuntimeException {

    private final @Nullable Long productId; // null only for a product that was never saved
    private final int requested;
    private final int available;

    public InsufficientStockException(@Nullable Long productId, int requested, int available) {
        super("Product " + productId + " has " + available + " in stock, " + requested + " requested");
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    public @Nullable Long getProductId() {
        return productId;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
