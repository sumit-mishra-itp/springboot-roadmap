package dev.roadmap.aop.ordering;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;

import org.springframework.stereotype.Component;

import dev.roadmap.aop.CallLog;

/**
 * All five advice types in ONE aspect on the same join point. Within an aspect, Spring ranks them by type:
 * Around, Before, After, AfterReturning, AfterThrowing (highest to lowest precedence). @After still runs after
 * AfterReturning/AfterThrowing, because it is "after finally" advice that wraps them.
 */
@Aspect
@Component
public class AdviceOrderAspect {

    private final CallLog callLog;

    public AdviceOrderAspect(CallLog callLog) {
        this.callLog = callLog;
    }

    /** A named pointcut: reusable, and referenced by method name below. */
    @Pointcut("within(dev.roadmap.aop.ordering.OrderProbe)")
    void probe() {
    }

    @Around("probe()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        callLog.record("around:before");
        try {
            Object result = pjp.proceed();
            callLog.record("around:after");
            return result;
        } catch (Throwable ex) {
            callLog.record("around:caught");
            throw ex;
        }
    }

    @Before("probe()")
    public void before() {
        callLog.record("before");
    }

    @After("probe()")
    public void after() {
        callLog.record("after");
    }

    @AfterReturning(pointcut = "probe()", returning = "result")
    public void afterReturning(Object result) {
        callLog.record("afterReturning:" + result);
    }

    /** The parameter type narrows matching: only IllegalStateException (and subtypes) trigger this advice. */
    @AfterThrowing(pointcut = "probe()", throwing = "ex")
    public void afterThrowing(IllegalStateException ex) {
        callLog.record("afterThrowing:" + ex.getMessage());
    }
}
