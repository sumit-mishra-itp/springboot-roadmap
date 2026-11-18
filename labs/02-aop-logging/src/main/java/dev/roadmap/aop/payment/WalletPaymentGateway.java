package dev.roadmap.aop.payment;

import org.springframework.context.annotation.ProxyType;
import org.springframework.context.annotation.Proxyable;
import org.springframework.stereotype.Component;

import dev.roadmap.aop.logging.Loggable;

/**
 * Framework 7 @Proxyable overrides the global (CGLIB) default for this bean only. It becomes a JDK dynamic proxy
 * that implements PaymentGateway but is NOT a WalletPaymentGateway, so inject it by the interface type.
 */
@Component
@Proxyable(ProxyType.INTERFACES)
public class WalletPaymentGateway implements PaymentGateway {

    @Loggable
    @Override
    public String charge(long amountCents) {
        return "wallet:" + amountCents;
    }
}
