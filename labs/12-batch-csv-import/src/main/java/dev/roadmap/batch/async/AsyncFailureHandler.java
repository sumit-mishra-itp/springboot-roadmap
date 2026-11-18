package dev.roadmap.batch.async;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

/**
 * Receives exceptions thrown by {@code void} @Async methods. There is no Future to carry them, so without a handler
 * they are only logged by SimpleAsyncUncaughtExceptionHandler. The counter stands in for a Micrometer counter.
 */
public class AsyncFailureHandler implements AsyncUncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AsyncFailureHandler.class);

    private final AtomicInteger failures = new AtomicInteger();

    @Override
    public void handleUncaughtException(Throwable ex, Method method, Object... params) {
        failures.incrementAndGet();
        log.error("@Async {} failed", method.getName(), ex);
    }

    public int failures() {
        return failures.get();
    }
}
