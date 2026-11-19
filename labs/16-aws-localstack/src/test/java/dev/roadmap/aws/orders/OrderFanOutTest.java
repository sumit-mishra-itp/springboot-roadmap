package dev.roadmap.aws.orders;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import dev.roadmap.aws.AwsIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderFanOutTest extends AwsIntegrationTest {

	@Autowired
	private OrderPublisher publisher;

	@Test
	void oneSnsPublish_reachesEverySubscribedQueue_rawAndEnveloped() {
		var order = new OrderPlaced(UUID.randomUUID(), "BOOK-2", 1);

		publisher.broadcast(order);

		verify(processor, timeout(TIMEOUT.toMillis())).charge(order); // billing: raw delivery
		verify(processor, timeout(TIMEOUT.toMillis())).ship(order); // shipping: SNS envelope + @SnsNotificationMessage
		verify(processor, never()).fulfil(order); // the orders queue is not subscribed to the topic
	}

}
