package dev.roadmap.kafka;

import dev.roadmap.kafka.config.KafkaConfig;
import dev.roadmap.kafka.inventory.InventoryService;
import dev.roadmap.kafka.order.OrderPlaced;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;

/**
 * Every integration test extends this class, so they all share ONE cached application context. A
 * second context (different properties or bean overrides) would start a second "inventory"
 * consumer group member, which would steal partitions and make messages seem to vanish.
 */
@SpringBootTest(properties = { "lab.outbox.poll-interval=200ms", "lab.kafka.retry.interval=100ms",
		"lab.kafka.retry.max-retries=2" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

	protected static final Duration TIMEOUT = Duration.ofSeconds(30);

	// A spy records every call the listener makes, which lets the tests count delivery attempts.
	@MockitoSpyBean
	protected InventoryService inventory;

	@Autowired
	protected JdbcClient jdbc;

	@Autowired
	protected KafkaTemplate<String, Object> kafka;

	@Autowired
	private ConsumerFactory<Object, Object> consumerFactory;

	protected static String uniqueSku() {
		return "SKU-" + UUID.randomUUID().toString().substring(0, 8);
	}

	protected static OrderPlaced orderPlaced(String sku, int quantity) {
		return new OrderPlaced(UUID.randomUUID(), UUID.randomUUID(), sku, quantity, Instant.now());
	}

	protected void givenStock(String sku, int available) {
		jdbc.sql("insert into stock (sku, available) values (?, ?)").params(sku, available).update();
	}

	protected int availableStock(String sku) {
		return jdbc.sql("select available from stock where sku = ?").param(sku).query(Integer.class).single();
	}

	protected long processedRows(UUID eventId) {
		return jdbc.sql("select count(*) from processed_message where event_id = ?")
			.param(eventId)
			.query(Long.class)
			.single();
	}

	/** Reads the DLT with a throwaway consumer group until the record for this event shows up. */
	protected ConsumerRecord<Object, Object> awaitDeadLetter(UUID eventId) {
		var found = new AtomicReference<ConsumerRecord<Object, Object>>();
		try (var consumer = consumerFactory.createConsumer("dlt-reader-" + UUID.randomUUID(), null)) {
			consumer.subscribe(List.of(KafkaConfig.ORDERS_DLT));
			await().atMost(TIMEOUT).pollInSameThread().until(() -> {
				for (var record : consumer.poll(Duration.ofMillis(200))) {
					if (record.value() instanceof OrderPlaced event && event.eventId().equals(eventId)) {
						found.set(record);
					}
				}
				return found.get() != null;
			});
		}
		return found.get();
	}

}
