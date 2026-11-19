package dev.roadmap.kafka.inventory;

/**
 * Retryable, because the catalog feed that creates stock rows may simply not have arrived yet. If
 * the SKU is still missing after the configured retries, the record goes to the dead-letter topic.
 */
public class SkuNotFoundException extends RuntimeException {

	SkuNotFoundException(String sku) {
		super("Unknown SKU " + sku);
	}

}
