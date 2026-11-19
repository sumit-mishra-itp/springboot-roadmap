package dev.roadmap.kafka.inventory;

import dev.roadmap.kafka.order.OrderPlaced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class InventoryService {

	private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

	private final JdbcClient jdbc;

	InventoryService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * The "processed" marker and the stock update share one transaction. If the update fails, the
	 * marker rolls back too, and the redelivered record is processed again.
	 * @return false when the event was a duplicate and was ignored
	 */
	@Transactional
	public boolean reserve(OrderPlaced event) {
		if (!markProcessed(event.eventId())) {
			log.info("Duplicate event {} ignored", event.eventId());
			return false;
		}
		int updated = jdbc.sql("update stock set available = available - :qty where sku = :sku and available >= :qty")
			.param("qty", event.quantity())
			.param("sku", event.sku())
			.update();
		if (updated == 1) {
			return true;
		}
		throw skuExists(event.sku()) ? new InsufficientStockException(event.sku(), event.quantity())
				: new SkuNotFoundException(event.sku());
	}

	// Use ON CONFLICT DO NOTHING rather than catching the duplicate-key exception. In Postgres a failed
	// statement aborts the whole transaction. This form is also race-free across consumer threads.
	private boolean markProcessed(UUID eventId) {
		return jdbc
			.sql("insert into processed_message (event_id, processed_at) values (:id, now()) on conflict do nothing")
			.param("id", eventId)
			.update() == 1;
	}

	private boolean skuExists(String sku) {
		return jdbc.sql("select count(*) from stock where sku = :sku").param("sku", sku).query(Long.class).single() > 0;
	}

}
