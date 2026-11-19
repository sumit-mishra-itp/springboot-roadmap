package dev.roadmap.aws;

import java.time.Duration;

import dev.roadmap.aws.orders.OrderProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Every integration test extends this class, so they all share ONE cached context. A second context would start a
 * second set of @SqsListener containers polling the same queues, and messages would seem to vanish.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class AwsIntegrationTest {

	// Runs when the test class is initialized, which is before Spring builds the Environment.
	static {
		LocalStackInfra.container();
	}

	protected static final Duration TIMEOUT = Duration.ofSeconds(30);

	protected static final String DLQ = LocalStackInfra.DLQ;

	protected static final int MAX_RECEIVE_COUNT = LocalStackInfra.MAX_RECEIVE_COUNT;

	// The spy records every call the listeners make, so tests can count delivery attempts.
	@MockitoSpyBean
	protected OrderProcessor processor;

}
