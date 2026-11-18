package dev.roadmap.aop.inventory;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

import dev.roadmap.aop.logging.Loggable;

/**
 * Simulates a flaky downstream inventory API. A real client would wrap a RestClient call. Here,
 * failNextCalls(n) makes the next n calls throw, so tests can drive the retry logic deterministically.
 */
@Component
public class InventoryClient {

    private final AtomicInteger remainingFailures = new AtomicInteger();
    private final AtomicInteger attempts = new AtomicInteger();

    /**
     * Retries only the transient exception, at most 3 times (so 4 attempts in total), with a 5 ms delay
     * doubling each time. The default delay is 1000 ms, which is far too slow for a test.
     */
    @Loggable
    @Retryable(includes = DownstreamUnavailableException.class, maxRetries = 3, delay = 5, multiplier = 2)
    public int stockFor(String sku) {
        attempts.incrementAndGet();
        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank"); // not in includes, so never retried
        }
        if (remainingFailures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
            throw new DownstreamUnavailableException("inventory-service returned 503");
        }
        return 42;
    }

    public void failNextCalls(int count) {
        remainingFailures.set(count);
        attempts.set(0);
    }

    public int attempts() {
        return attempts.get();
    }
}
