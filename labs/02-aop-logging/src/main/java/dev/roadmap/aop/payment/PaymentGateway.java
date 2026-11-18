package dev.roadmap.aop.payment;

/** An interface on purpose: it shows whether Spring picks a JDK proxy or CGLIB for beans that implement one. */
public interface PaymentGateway {

    String charge(long amountCents);
}
