package dev.roadmap.aop.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

/** Programmatic retry, no container and no proxy: plain JUnit is enough. */
class RetryTemplateTest {

    private final RetryTemplate retryTemplate = new RetryTemplate(RetryPolicy.builder()
            .includes(DownstreamUnavailableException.class)
            .maxRetries(2)
            .delay(Duration.ofMillis(1))
            .build());

    @Test
    void invoke_returnsTheResult_afterTransientFailures() {
        var calls = new AtomicInteger();

        String result = retryTemplate.invoke(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new DownstreamUnavailableException("503");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void execute_throwsCheckedRetryException_holdingEveryAttemptsFailure() {
        RetryException ex = catchThrowableOfType(RetryException.class, () -> retryTemplate.execute(() -> {
            throw new DownstreamUnavailableException("503");
        }));

        assertThat(ex.getExceptions()).hasSize(3); // 1 initial + 2 retries
        assertThat(ex.getLastException()).isInstanceOf(DownstreamUnavailableException.class);
    }
}
