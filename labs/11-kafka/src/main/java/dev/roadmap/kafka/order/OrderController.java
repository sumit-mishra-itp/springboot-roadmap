package dev.roadmap.kafka.order;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/orders")
class OrderController {

	private final OrderService orderService;

	OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	// 201 means "accepted and durably recorded". Stock is reserved later and asynchronously, via Kafka.
	@PostMapping
	ResponseEntity<Void> place(@Valid @RequestBody PlaceOrderRequest request) {
		var id = orderService.placeOrder(request);
		return ResponseEntity.created(URI.create("/orders/" + id)).build();
	}

}
