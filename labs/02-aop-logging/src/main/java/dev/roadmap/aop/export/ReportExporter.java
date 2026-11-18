package dev.roadmap.aop.export;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.ConcurrencyLimit.ThrottlePolicy;
import org.springframework.stereotype.Component;

/**
 * Exports are heavy (memory, DB load), so their concurrency is capped. The job body is passed in as a Runnable,
 * which lets a test hold a call "inside" the method and observe what a second caller experiences.
 */
@Component
public class ReportExporter {

    /** Fail fast: a second concurrent caller gets InvocationRejectedException right away (since 7.0.3). */
    @ConcurrencyLimit(limit = 1, policy = ThrottlePolicy.REJECT)
    public void exportOrReject(Runnable job) {
        job.run();
    }

    /** Default BLOCK policy: extra callers wait for a permit, like a pool with one connection. */
    @ConcurrencyLimit(1)
    public void exportOrWait(Runnable job) {
        job.run();
    }
}
