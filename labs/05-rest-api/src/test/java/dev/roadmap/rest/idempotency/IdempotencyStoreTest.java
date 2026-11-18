package dev.roadmap.rest.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/** Plain JUnit: the race is in the store, so no Spring context is needed to prove it. */
class IdempotencyStoreTest {

    private final IdempotencyStore store = new IdempotencyStore();

    @Test
    void concurrentRetriesWithSameKey_runTheActionExactlyOnce() throws Exception {
        var executions = new AtomicInteger();
        var start = new CountDownLatch(1);
        Callable<IdempotencyStore.Outcome<Integer>> retry = () -> {
            start.await();
            return store.execute("pay-42", "charge 10 USD", executions::incrementAndGet);
        };

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<IdempotencyStore.Outcome<Integer>>> futures =
                    IntStream.range(0, 32).mapToObj(i -> executor.submit(retry)).toList();
            start.countDown(); // release all 32 "client retries" at once

            List<IdempotencyStore.Outcome<Integer>> outcomes = futures.stream().map(IdempotencyStoreTest::join).toList();

            assertThat(executions).hasValue(1);
            assertThat(outcomes).extracting(IdempotencyStore.Outcome::value).containsOnly(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.replayed()).hasSize(1);
        }
    }

    @Test
    void sameKeyWithDifferentFingerprint_isRejected() {
        store.execute("pay-43", "charge 10 USD", () -> "ch_1");

        assertThatThrownBy(() -> store.execute("pay-43", "charge 99 USD", () -> "ch_2"))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void failedAction_isNotStored_soTheClientCanRetry() {
        assertThatThrownBy(() -> store.execute("pay-44", "x", () -> {
            throw new IllegalStateException("payment gateway timeout");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(store.execute("pay-44", "x", () -> "ch_3")).isEqualTo(new IdempotencyStore.Outcome<>("ch_3", false));
    }

    private static <T> T join(Future<T> future) {
        try {
            return future.get();
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
