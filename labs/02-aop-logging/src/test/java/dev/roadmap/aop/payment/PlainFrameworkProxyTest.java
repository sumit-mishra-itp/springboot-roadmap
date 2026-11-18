package dev.roadmap.aop.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

import dev.roadmap.aop.CallLog;
import dev.roadmap.aop.logging.LoggingAspect;

/** No Boot: plain Framework container, to contrast with Boot's CGLIB default in ProxyTypeTest. */
class PlainFrameworkProxyTest {

    @Test
    void plainFramework_defaultsToJdkProxy_forInterfaceImplementingBeans() {
        try (var context = new AnnotationConfigApplicationContext(PlainFrameworkConfig.class)) {
            PaymentGateway card = context.getBean(PaymentGateway.class);

            assertThat(AopUtils.isJdkDynamicProxy(card)).isTrue();
            assertThat(card).isNotInstanceOf(CardPaymentGateway.class);
        }
    }

    /** @EnableAspectJAutoProxy with its Framework default proxyTargetClass = false. */
    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    @Import({CallLog.class, LoggingAspect.class, CardPaymentGateway.class})
    static class PlainFrameworkConfig {
    }
}
