package dev.roadmap.jpa.catalog;

public class OutOfStockException extends RuntimeException {

    public OutOfStockException(String sku) {
        super("Out of stock: " + sku);
    }
}
