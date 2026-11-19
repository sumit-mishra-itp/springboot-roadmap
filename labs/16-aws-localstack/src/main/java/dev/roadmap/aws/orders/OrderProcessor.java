package dev.roadmap.aws.orders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Stand-in for the real downstream work: warehouse, payment provider, courier. */
@Service
public class OrderProcessor {

	private static final Logger log = LoggerFactory.getLogger(OrderProcessor.class);

	public void fulfil(OrderPlaced order) {
		if (order.quantity() <= 0) {
			// A poison message: retrying never fixes it. The queue's redrive policy moves it to the DLQ.
			throw new IllegalArgumentException("quantity must be positive for order " + order.orderId());
		}
		log.info("fulfilling order {}", order.orderId());
	}

	public void charge(OrderPlaced order) {
		log.info("charging order {}", order.orderId());
	}

	public void ship(OrderPlaced order) {
		log.info("shipping order {}", order.orderId());
	}

}
