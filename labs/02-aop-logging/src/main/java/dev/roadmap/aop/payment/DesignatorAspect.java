package dev.roadmap.aop.payment;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;

import org.springframework.stereotype.Component;

import dev.roadmap.aop.CallLog;

/**
 * One @Before per pointcut designator. Each expression is scoped with within(...) so it never tries to match
 * Boot's own beans. Several @Before advices in ONE aspect on the same join point run in an UNDEFINED order.
 */
@Aspect
@Component
public class DesignatorAspect {

    private final CallLog callLog;

    public DesignatorAspect(CallLog callLog) {
        this.callLog = callLog;
    }

    /** this = the proxy. A JDK proxy implements only the interfaces, so this(WalletPaymentGateway) can't match it. */
    @Before("within(dev.roadmap.aop.payment..*) && this(dev.roadmap.aop.payment.WalletPaymentGateway)")
    public void thisWallet() {
        callLog.record("this:wallet");
    }

    /** target = the real object behind the proxy, which IS a WalletPaymentGateway. */
    @Before("within(dev.roadmap.aop.payment..*) && target(dev.roadmap.aop.payment.WalletPaymentGateway)")
    public void targetWallet() {
        callLog.record("target:wallet");
    }

    /** bean(...) is a Spring AOP extension (not AspectJ). It matches by bean name, and wildcards are allowed. */
    @Before("within(dev.roadmap.aop.payment..*) && bean(card*)")
    public void cardBean() {
        callLog.record("bean:card");
    }

    /** execution + args: match the interface method and bind its runtime argument. */
    @Before("execution(* dev.roadmap.aop.payment.PaymentGateway.charge(long)) && args(amountCents)")
    public void chargeArgs(long amountCents) {
        callLog.record("args:" + amountCents);
    }
}
