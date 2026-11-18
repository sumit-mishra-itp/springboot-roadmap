package dev.roadmap.aop.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ProxyTypeTest {

    @Autowired
    Map<String, PaymentGateway> gateways;

    @Test
    void boot_defaultsToCglib_evenWhenTheBeanImplementsAnInterface() {
        PaymentGateway card = gateways.get("cardPaymentGateway");

        assertThat(AopUtils.isCglibProxy(card)).isTrue();
        assertThat(card).isInstanceOf(CardPaymentGateway.class); // a subclass, so class-typed injection works
    }

    @Test
    void proxyableInterfaces_forcesJdkProxy_forThatBeanOnly() {
        PaymentGateway wallet = gateways.get("walletPaymentGateway");

        assertThat(AopUtils.isJdkDynamicProxy(wallet)).isTrue();
        assertThat(wallet).isNotInstanceOf(WalletPaymentGateway.class); // injecting WalletPaymentGateway would fail
        assertThat(AopUtils.getTargetClass(wallet)).isEqualTo(WalletPaymentGateway.class);
    }
}
