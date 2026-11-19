package dev.roadmap.kafka.inventory;

import dev.roadmap.kafka.IntegrationTest;
import dev.roadmap.kafka.config.KafkaConfig;
import dev.roadmap.kafka.order.OrderPlaced;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.KafkaHeaders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class InventoryConsumerTest extends IntegrationTest {

	@Autowired
	KafkaListenerEndpointRegistry registry;

	@Test
	void duplicateEvent_isProcessedOnce() throws Exception {
		var sku = uniqueSku();
		givenStock(sku, 10);
		var event = orderPlaced(sku, 4);

		// The same event delivered twice, as an outbox relay retry or a consumer rebalance would cause.
		kafka.send(OrderPlaced.TOPIC, event.orderId().toString(), event).get();
		kafka.send(OrderPlaced.TOPIC, event.orderId().toString(), event).get();

		await().atMost(TIMEOUT).untilAsserted(() -> verify(inventory, times(2)).reserve(event));
		assertThat(availableStock(sku)).isEqualTo(6);
		assertThat(processedRows(event.eventId())).isEqualTo(1);
	}

	@Test
	void poisonEvent_isRetriedThenDeadLettered() {
		var event = orderPlaced(uniqueSku(), 1); // no stock row: SkuNotFoundException, which is retryable

		kafka.send(OrderPlaced.TOPIC, event.orderId().toString(), event);

		var deadLetter = awaitDeadLetter(event.eventId());
		// 1 delivery + max-retries (2) = 3 attempts, then the recoverer publishes to orders.placed-dlt.
		verify(inventory, times(3)).reserve(argThat(e -> e.eventId().equals(event.eventId())));
		// The listener exception arrives wrapped: the fqcn header names the wrapper, the cause header names ours.
		assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_FQCN))
			.isEqualTo(ListenerExecutionFailedException.class.getName());
		assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
			.isEqualTo(SkuNotFoundException.class.getName());
		assertThat(header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(OrderPlaced.TOPIC);
		assertThat(deadLetter.key()).isEqualTo(event.orderId().toString());
	}

	@Test
	void notRetryableException_isDeadLetteredOnFirstFailure_andRollsBackTheProcessedMarker() {
		var sku = uniqueSku();
		givenStock(sku, 1);
		var event = orderPlaced(sku, 5); // InsufficientStockException, registered as not retryable

		kafka.send(OrderPlaced.TOPIC, event.orderId().toString(), event);

		awaitDeadLetter(event.eventId());
		verify(inventory, times(1)).reserve(argThat(e -> e.eventId().equals(event.eventId())));
		assertThat(availableStock(sku)).isEqualTo(1);
		assertThat(processedRows(event.eventId())).isZero();
	}

	@Test
	void listenerConcurrency_startsOneConsumerPerPartition() {
		var container = (ConcurrentMessageListenerContainer<?, ?>) registry.getListenerContainer("inventory");

		assertThat(container.getConcurrency()).isEqualTo(KafkaConfig.PARTITIONS);
		await().atMost(TIMEOUT).untilAsserted(() -> assertThat(container.getContainers())
			.allSatisfy(child -> assertThat(child.getAssignedPartitions()).hasSize(1)));
	}

	private static String header(ConsumerRecord<?, ?> record, String name) {
		var header = record.headers().lastHeader(name);
		return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
	}

}
