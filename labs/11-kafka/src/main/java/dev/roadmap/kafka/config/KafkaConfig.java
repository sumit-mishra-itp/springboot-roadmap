package dev.roadmap.kafka.config;

import dev.roadmap.kafka.inventory.InsufficientStockException;
import dev.roadmap.kafka.order.OrderPlaced;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

	public static final int PARTITIONS = 3;

	/** The DeadLetterPublishingRecoverer default: the topic name plus "-dlt" (".DLT" in old tutorials), same partition. */
	public static final String ORDERS_DLT = OrderPlaced.TOPIC + "-dlt";

	// The auto-configured KafkaAdmin creates NewTopic beans at startup if they don't exist.
	// replicas(1) suits the single test broker. In production, use 3 replicas and min.insync.replicas=2.
	@Bean
	NewTopic ordersTopic() {
		return TopicBuilder.name(OrderPlaced.TOPIC).partitions(PARTITIONS).replicas(1).build();
	}

	// The recoverer keeps the original partition number, so the DLT needs at least as many partitions.
	@Bean
	NewTopic ordersDeadLetterTopic() {
		return TopicBuilder.name(ORDERS_DLT).partitions(PARTITIONS).replicas(1).build();
	}

	/**
	 * Boot injects a single CommonErrorHandler bean into the auto-configured listener container
	 * factory. These are blocking retries: the failing record is re-polled, so per-key order is
	 * kept. After maxRetries the record is published to the DLT and its offset is committed.
	 */
	@Bean
	DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template, RetryProperties retry) {
		var recoverer = new DeadLetterPublishingRecoverer(template);
		var handler = new DefaultErrorHandler(recoverer,
				new FixedBackOff(retry.interval().toMillis(), retry.maxRetries()));
		handler.addNotRetryableExceptions(InsufficientStockException.class);
		return handler;
	}

}
