package dev.roadmap.kafka.inventory;

import dev.roadmap.kafka.order.OrderPlaced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
class InventoryListener {

	private static final Logger log = LoggerFactory.getLogger(InventoryListener.class);

	private final InventoryService inventory;

	InventoryListener(InventoryService inventory) {
		this.inventory = inventory;
	}

	// No try/catch. The exception must reach the container's DefaultErrorHandler, which retries and
	// then dead-letters. Swallowing it here would commit the offset and lose the event.
	@KafkaListener(id = "inventory", groupId = "inventory", topics = OrderPlaced.TOPIC)
	void onOrderPlaced(OrderPlaced event, @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
			@Header(KafkaHeaders.OFFSET) long offset) {
		log.debug("Reserving for order {} from partition {} offset {}", event.orderId(), partition, offset);
		inventory.reserve(event);
	}

}
