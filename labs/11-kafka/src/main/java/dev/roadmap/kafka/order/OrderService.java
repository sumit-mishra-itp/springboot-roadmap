package dev.roadmap.kafka.order;

import dev.roadmap.kafka.outbox.OutboxEvent;
import dev.roadmap.kafka.outbox.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrderService {

	private final OrderRepository orders;

	private final OutboxRepository outbox;

	private final JsonMapper jsonMapper;

	OrderService(OrderRepository orders, OutboxRepository outbox, JsonMapper jsonMapper) {
		this.orders = orders;
		this.outbox = outbox;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * The order row and the outbox row commit or roll back together. There is no Kafka call here.
	 * A "save, then kafkaTemplate.send()" dual write can lose the event (a crash after the commit)
	 * or publish a phantom one (the send succeeds and the commit fails).
	 */
	@Transactional
	public UUID placeOrder(PlaceOrderRequest request) {
		var order = orders.save(new Order(request.sku(), request.quantity()));
		var event = new OrderPlaced(UUID.randomUUID(), order.getId(), order.getSku(), order.getQuantity(),
				Instant.now());
		outbox.save(new OutboxEvent("Order", order.getId(), "OrderPlaced", OrderPlaced.TOPIC,
				jsonMapper.writeValueAsString(event)));
		return order.getId();
	}

}
