package dev.roadmap.kafka.order;

import dev.roadmap.kafka.IntegrationTest;
import dev.roadmap.kafka.config.KafkaConfig;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.utils.Utils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

class PartitionOrderingTest extends IntegrationTest {

	@Test
	void sameKeyEvents_landInOnePartition_inSendOrder_andAreConsumedInThatOrder() {
		var sku = uniqueSku();
		givenStock(sku, 100);
		var orderId = UUID.randomUUID();
		var key = orderId.toString();
		var events = IntStream.range(0, 10)
			.mapToObj(i -> new OrderPlaced(UUID.randomUUID(), orderId, sku, 1, Instant.now()))
			.toList();

		// send() is asynchronous. The CompletableFuture completes with the broker-assigned metadata.
		var futures = events.stream().map(e -> kafka.send(OrderPlaced.TOPIC, key, e)).toList();
		List<RecordMetadata> metadata = futures.stream().map(f -> f.join().getRecordMetadata()).toList();

		// Producer side: one partition, chosen by the default partitioner as murmur2(key) mod partitions.
		var expectedPartition = Utils.toPositive(Utils.murmur2(key.getBytes(StandardCharsets.UTF_8)))
				% KafkaConfig.PARTITIONS;
		assertThat(metadata).extracting(RecordMetadata::partition).containsOnly(expectedPartition);
		assertThat(metadata).extracting(RecordMetadata::offset).isSorted().doesNotHaveDuplicates();

		// Consumer side: the single thread that owns that partition sees the events in the same order.
		await().atMost(TIMEOUT).until(() -> availableStock(sku) == 90);
		var captor = ArgumentCaptor.forClass(OrderPlaced.class);
		verify(inventory, atLeast(10)).reserve(captor.capture());
		var consumedForThisOrder = captor.getAllValues().stream().filter(e -> e.orderId().equals(orderId)).toList();
		assertThat(consumedForThisOrder).containsExactlyElementsOf(events);
	}

}
