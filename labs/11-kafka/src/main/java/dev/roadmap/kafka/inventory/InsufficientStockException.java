package dev.roadmap.kafka.inventory;

/** A business rejection. Retrying cannot fix it, so the error handler marks it not-retryable. */
public class InsufficientStockException extends RuntimeException {

	InsufficientStockException(String sku, int quantity) {
		super("Not enough stock of " + sku + " for quantity " + quantity);
	}

}
