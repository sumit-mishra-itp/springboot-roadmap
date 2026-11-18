package dev.roadmap.aop.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import dev.roadmap.aop.CallLog;

@SpringBootTest
class DesignatorTest {

    @Autowired
    Map<String, PaymentGateway> gateways;

    @Autowired
    CallLog callLog;

    @BeforeEach
    void clearLog() {
        callLog.clear();
    }

    @Test
    void jdkProxy_matchesTarget_butNotThis() {
        gateways.get("walletPaymentGateway").charge(700);

        // Same-type @Before advices in one aspect have an undefined order, so only membership is asserted.
        assertThat(callLog.events())
                .contains("target:wallet", "args:700")
                .doesNotContain("this:wallet", "bean:card");
    }

    @Test
    void beanDesignator_matchesByBeanName() {
        gateways.get("cardPaymentGateway").charge(300);

        assertThat(callLog.events())
                .contains("bean:card", "args:300")
                .doesNotContain("this:wallet", "target:wallet");
    }
}
