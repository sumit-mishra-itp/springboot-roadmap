package dev.roadmap.aop.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.resilience.InvocationRejectedException;

@SpringBootTest
class ConcurrencyLimitTest {

    @Autowired
    ReportExporter exporter;

    @Test
    void rejectPolicy_failsTheSecondConcurrentCaller() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> first = executor.submit(() -> exporter.exportOrReject(() -> {
                entered.countDown();
                await(release); // hold the only permit
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> exporter.exportOrReject(() -> { }))
                    .isInstanceOf(InvocationRejectedException.class);

            release.countDown();
            first.get(5, TimeUnit.SECONDS);
        }
        exporter.exportOrReject(() -> { }); // permit was returned, so this succeeds
    }

    @Test
    void blockPolicy_neverLetsMoreThanTheLimitIn() throws Exception {
        var inFlight = new AtomicInteger();
        var maxInFlight = new AtomicInteger();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                calls.add(executor.submit(() -> exporter.exportOrWait(() -> {
                    maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                    sleep(20);
                    inFlight.decrementAndGet();
                })));
            }
            for (Future<?> call : calls) {
                call.get(10, TimeUnit.SECONDS); // every caller eventually got in: blocked, not rejected
            }
        }
        assertThat(maxInFlight).hasValue(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
