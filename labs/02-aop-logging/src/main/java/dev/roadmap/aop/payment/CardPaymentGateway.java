package dev.roadmap.aop.payment;

import org.springframework.stereotype.Component;

import dev.roadmap.aop.logging.Loggable;

/** Implements an interface, yet Boot proxies it with CGLIB because spring.aop.proxy-target-class defaults to true. */
@Component
public class CardPaymentGateway implements PaymentGateway {

    @Loggable
    @Override
    public String charge(long amountCents) {
        return "card:" + amountCents;
    }
}
