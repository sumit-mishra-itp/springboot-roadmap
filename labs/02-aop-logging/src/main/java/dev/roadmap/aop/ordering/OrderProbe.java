package dev.roadmap.aop.ordering;

import org.springframework.stereotype.Component;

import dev.roadmap.aop.CallLog;

/** Target for AdviceOrderAspect: one method that returns, one that throws. */
@Component
public class OrderProbe {

    private final CallLog callLog;

    public OrderProbe(CallLog callLog) {
        this.callLog = callLog;
    }

    public String succeed() {
        callLog.record("target");
        return "ok";
    }

    public String fail() {
        callLog.record("target");
        throw new IllegalStateException("boom");
    }
}
