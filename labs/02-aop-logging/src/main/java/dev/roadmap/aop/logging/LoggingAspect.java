package dev.roadmap.aop.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import dev.roadmap.aop.CallLog;

/**
 * Times every @Loggable method. @Aspect alone is not enough: the aspect must also be a bean (@Component).
 * @Order goes on the aspect CLASS. On an advice method it is ignored.
 */
@Aspect
@Component
@Order(2)
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    private final CallLog callLog;

    public LoggingAspect(CallLog callLog) {
        this.callLog = callLog;
    }

    // within(...) is the cheap "scoping" designator. @annotation(loggable) both matches and binds the annotation.
    @Around("within(dev.roadmap.aop..*) && @annotation(loggable)")
    public Object time(ProceedingJoinPoint pjp, Loggable loggable) throws Throwable {
        String method = pjp.getSignature().getName();
        callLog.record("logging:enter:" + method);
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            callLog.record("logging:exit:" + method);
            if (tookMs > loggable.slowThresholdMs()) {
                log.warn("{} took {} ms (threshold {} ms)", pjp.getSignature().toShortString(), tookMs, loggable.slowThresholdMs());
            } else {
                log.debug("{} took {} ms", pjp.getSignature().toShortString(), tookMs);
            }
        }
    }
}
