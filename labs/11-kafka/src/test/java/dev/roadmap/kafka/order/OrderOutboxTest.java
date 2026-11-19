package dev.roadmap.kafka.order;

import dev.roadmap.kafka.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderOutboxTest extends IntegrationTest {

	@Autowired
	OrderService orderService;

	@Autowired
	TransactionTemplate transactionTemplate;

	@Autowired
	MockMvc mockMvc;

	@Test
	void placeOrder_writesOrderAndOutboxRowTogether() {
		var orderId = orderService.placeOrder(new PlaceOrderRequest(uniqueSku(), 1));

		assertThat(countOrders(orderId)).isEqualTo(1);
		assertThat(countOutboxRows(orderId)).isEqualTo(1);
	}

	@Test
	void placeOrder_rolledBack_leavesNeitherOrderNorOutboxRow() {
		var sku = uniqueSku();

		// The service joins the outer transaction (REQUIRED), so one rollback undoes both inserts.
		var orderId = transactionTemplate.execute(status -> {
			var id = orderService.placeOrder(new PlaceOrderRequest(sku, 1));
			status.setRollbackOnly();
			return id;
		});

		assertThat(countOrders(orderId)).isZero();
		assertThat(countOutboxRows(orderId)).isZero();
	}

	@Test
	void postedOrder_isRelayedToKafka_andConsumerReservesStock() throws Exception {
		var sku = uniqueSku();
		givenStock(sku, 10);

		var location = mockMvc
			.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
				.content("{\"sku\":\"" + sku + "\",\"quantity\":3}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getHeader("Location");
		var orderId = UUID.fromString(location.substring("/orders/".length()));

		await().atMost(TIMEOUT).untilAsserted(() -> {
			assertThat(availableStock(sku)).isEqualTo(7);
			assertThat(jdbc.sql("select count(*) from outbox_event where aggregate_id = ? and sent_at is not null")
				.param(orderId)
				.query(Long.class)
				.single()).isEqualTo(1);
		});
	}

	@Test
	void invalidOrder_isRejectedWithProblemDetail() throws Exception {
		mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"\",\"quantity\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE));
	}

	private long countOrders(UUID orderId) {
		return jdbc.sql("select count(*) from orders where id = ?").param(orderId).query(Long.class).single();
	}

	private long countOutboxRows(UUID orderId) {
		return jdbc.sql("select count(*) from outbox_event where aggregate_id = ?")
			.param(orderId)
			.query(Long.class)
			.single();
	}

}
