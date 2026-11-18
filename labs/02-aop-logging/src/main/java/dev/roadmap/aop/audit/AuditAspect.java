package dev.roadmap.aop.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import dev.roadmap.aop.CallLog;

/**
 * @Order(1) beats LoggingAspect's @Order(2): lower value = higher precedence = OUTERMOST.
 * So audit enters first and exits last, and its trail entry includes the time spent in logging.
 */
@Aspect
@Component
@Order(1)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final CallLog callLog;

    public AuditAspect(CallLog callLog) {
        this.callLog = callLog;
    }

    @Around("within(dev.roadmap.aop..*) && @annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        callLog.record("audit:enter:" + audited.action());
        try {
            Object result = pjp.proceed();
            log.info("AUDIT action={} outcome=SUCCESS", audited.action());
            return result;
        } catch (Throwable ex) {
            log.info("AUDIT action={} outcome=FAILURE reason={}", audited.action(), ex.getClass().getSimpleName());
            throw ex;
        } finally {
            callLog.record("audit:exit:" + audited.action());
        }
    }
}
