package dev.roadmap.aop.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.roadmap.aop.CallLog;

@SpringBootTest
class RetryableTest {

    @Autowired
    InventoryClient client;

    @Autowired
    CallLog callLog;

    @BeforeEach
    void reset() {
        callLog.clear();
        client.failNextCalls(0);
    }

    @Test
    void transientFailures_areRetried_untilSuccess() {
        client.failNextCalls(2);

        int stock = client.stockFor("SKU-1");

        assertThat(stock).isEqualTo(42);
        assertThat(client.attempts()).isEqualTo(3); // 1 initial + 2 retries
    }

    @Test
    void exhaustedRetries_rethrowTheLastOriginalException() {
        client.failNextCalls(10);

        assertThatThrownBy(() -> client.stockFor("SKU-1"))
                .isInstanceOf(DownstreamUnavailableException.class); // not wrapped in RetryException
        assertThat(client.attempts()).isEqualTo(4); // 1 initial + maxRetries(3)
    }

    @Test
    void exceptionOutsideIncludes_isNotRetried() {
        assertThatIllegalArgumentException().isThrownBy(() -> client.stockFor(" "));
        assertThat(client.attempts()).isEqualTo(1);
    }

    @Test
    void retryIsOutsideTheCustomAspect_soLoggingRunsOncePerAttempt() {
        client.failNextCalls(2);

        client.stockFor("SKU-1");

        assertThat(callLog.events()).containsExactly(
                "logging:enter:stockFor", "logging:exit:stockFor",
                "logging:enter:stockFor", "logging:exit:stockFor",
                "logging:enter:stockFor", "logging:exit:stockFor");
    }
}
