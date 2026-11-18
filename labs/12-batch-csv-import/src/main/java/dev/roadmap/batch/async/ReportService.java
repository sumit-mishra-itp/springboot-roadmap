package dev.roadmap.batch.async;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Each @Async method is intercepted by the proxy that AsyncAnnotationBeanPostProcessor wraps around this bean. */
@Service
public class ReportService {

    /** Simulated slow I/O (a downstream HTTP call) on the bounded report pool. */
    @Async("reportExecutor")
    public CompletableFuture<Report.Section> fetchSection(String name) throws InterruptedException {
        Thread.sleep(100);
        if (name.startsWith("broken")) {
            throw new IllegalStateException("downstream for " + name + " is down");
        }
        return CompletableFuture.completedFuture(new Report.Section(name, "data for " + name, ExecutionInfo.current()));
    }

    /** No qualifier: Boot's applicationTaskExecutor (virtual threads when enabled). */
    @Async
    public CompletableFuture<ExecutionInfo> currentExecution() {
        return CompletableFuture.completedFuture(ExecutionInfo.current());
    }

    /** void: the caller gets nothing back, so a failure goes to the AsyncUncaughtExceptionHandler. */
    @Async
    public void audit(String event) {
        if (event.isBlank()) {
            throw new IllegalArgumentException("audit event must not be blank");
        }
    }

    /** The self-invocation trap: 'this' is the raw bean, not the proxy, so this runs synchronously on the caller. */
    public CompletableFuture<ExecutionInfo> currentExecutionViaSelfCall() {
        return currentExecution();
    }
}
