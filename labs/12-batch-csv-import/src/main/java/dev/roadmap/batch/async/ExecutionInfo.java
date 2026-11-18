package dev.roadmap.batch.async;

import org.slf4j.MDC;

/** Where a piece of async work actually ran, and whether the caller's MDC came with it. */
public record ExecutionInfo(String thread, boolean virtual, String requestId) {

    static ExecutionInfo current() {
        Thread thread = Thread.currentThread();
        return new ExecutionInfo(thread.getName(), thread.isVirtual(), MDC.get("requestId"));
    }
}
