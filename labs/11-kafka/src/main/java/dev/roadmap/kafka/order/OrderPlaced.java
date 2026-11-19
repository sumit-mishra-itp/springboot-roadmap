package dev.roadmap.kafka.order;

import java.time.Instant;
import java.util.UUID;

/**
 * The event contract. Consumers de-duplicate on {@code eventId}. {@code orderId} is the Kafka key,
 * so every event of one order lands in the same partition and stays in order.
 */
public record OrderPlaced(UUID eventId, UUID orderId, String sku, int quantity, Instant occurredAt) {

	public static final String TOPIC = "orders.placed";

}
