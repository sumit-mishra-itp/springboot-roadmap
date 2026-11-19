package dev.roadmap.kafka.outbox;

import dev.roadmap.kafka.order.OrderPlaced;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Polling relay: outbox rows, then Kafka, then mark them sent. Delivery is at-least-once. If the
 * broker acks but the commit below fails, the rows are sent again on the next poll, so consumers
 * must be idempotent.
 */
@Component
class OutboxRelay {

	private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

	private static final int BATCH_SIZE = 100;

	private final OutboxRepository outbox;

	private final KafkaTemplate<String, Object> kafka;

	private final JsonMapper jsonMapper;

	OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, Object> kafka, JsonMapper jsonMapper) {
		this.outbox = outbox;
		this.kafka = kafka;
		this.jsonMapper = jsonMapper;
	}

	// ponytail: holds a DB transaction (and row locks) while waiting up to 10 s for broker acks.
	// That is fine for batches of 100. Switch to CDC (Debezium) when outbox volume or latency outgrows polling.
	@Scheduled(fixedDelayString = "${lab.outbox.poll-interval}")
	@Transactional
	public void publishPending() {
		var batch = outbox.lockUnsent(BATCH_SIZE);
		if (batch.isEmpty()) {
			return;
		}
		// Send the whole batch first, then wait once. The producer can then pack the records into
		// a few requests (linger.ms) instead of making one blocking round trip per record.
		var acks = batch.stream().map(this::send).toArray(CompletableFuture[]::new);
		CompletableFuture.allOf(acks).orTimeout(10, TimeUnit.SECONDS).join();
		var now = Instant.now();
		batch.forEach(event -> event.markSent(now));
		log.info("Relayed {} outbox events", batch.size());
	}

	private CompletableFuture<SendResult<String, Object>> send(OutboxEvent row) {
		// ponytail: one event type. Map eventType to a class once there are more.
		var event = jsonMapper.readValue(row.getPayload(), OrderPlaced.class);
		return kafka.send(row.getTopic(), row.getAggregateId().toString(), event).whenComplete((result, ex) -> {
			if (ex != null) {
				log.warn("Send failed for order {}, will retry on the next poll", row.getAggregateId(), ex);
				return;
			}
			var meta = result.getRecordMetadata();
			log.debug("Sent order {} to {}-{}@{}", row.getAggregateId(), meta.topic(), meta.partition(),
					meta.offset());
		});
	}

}
