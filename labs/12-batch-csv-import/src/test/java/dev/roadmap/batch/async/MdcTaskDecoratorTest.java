package dev.roadmap.batch.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Plain JUnit: a ThreadLocal (here the MDC) does not cross a thread boundary unless a TaskDecorator carries it. */
class MdcTaskDecoratorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void threadLocalIsLost_withoutTheDecorator() throws Exception {
        ThreadPoolTaskExecutor plain = executor(false);
        MDC.put("requestId", "req-1");

        Future<String> seen = plain.submit(() -> MDC.get("requestId"));

        assertThat(seen.get()).isNull();
        plain.shutdown();
    }

    @Test
    void threadLocalIsPresent_withTheDecorator_andClearedAfterwards() throws Exception {
        ThreadPoolTaskExecutor decorated = executor(true);
        MDC.put("requestId", "req-1");

        assertThat(decorated.submit(() -> MDC.get("requestId")).get()).isEqualTo("req-1");

        MDC.clear();
        assertThat(decorated.submit(() -> MDC.get("requestId")).get())
                .as("the pooled worker thread does not leak the previous task's context").isNull();
        decorated.shutdown();
    }

    private static ThreadPoolTaskExecutor executor(boolean decorate) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);   // one thread, so the second task provably reuses the first task's thread
        if (decorate) {
            executor.setTaskDecorator(new MdcTaskDecorator());
        }
        executor.initialize();
        return executor;
    }
}
