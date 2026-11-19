package dev.roadmap.aws.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;

import dev.roadmap.aws.AwsIntegrationTest;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderQueueTest extends AwsIntegrationTest {

	@Autowired
	private OrderPublisher publisher;

	@Autowired
	private SqsTemplate sqs;

	@Test
	void sqsListener_receivesEnqueuedOrder() {
		var order = new OrderPlaced(UUID.randomUUID(), "BOOK-1", 2);

		publisher.enqueue(order);

		verify(processor, timeout(TIMEOUT.toMillis())).fulfil(order);
	}

	@Test
	void poisonOrder_isRedeliveredMaxReceiveCountTimes_thenMovedToDlq() {
		var poison = new OrderPlaced(UUID.randomUUID(), "BOOK-1", 0);

		publisher.enqueue(poison);

		// ON_SUCCESS never deletes a failed message. It reappears after the 1 s visibility timeout, and on the receive
		// after maxReceiveCount SQS itself moves it to the DLQ. The app has no DLQ code at all.
		var dead = await().atMost(TIMEOUT)
			.until(() -> sqs.receive(from -> from.queue(DLQ).pollTimeout(Duration.ofSeconds(1)), OrderPlaced.class),
					message -> message.isPresent());
		assertThat(dead.orElseThrow().getPayload()).isEqualTo(poison);
		verify(processor, times(MAX_RECEIVE_COUNT)).fulfil(poison);
	}

}
