package dev.roadmap.coreioc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.roadmap.coreioc.events.OrderListeners;
import dev.roadmap.coreioc.events.OrderService;
import dev.roadmap.coreioc.injection.NotificationService;
import dev.roadmap.coreioc.registrar.FeatureFlags;
import dev.roadmap.coreioc.registrar.PaymentGateway;
import dev.roadmap.coreioc.scopes.CartService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/** Full Boot context: proves the pieces work together when component-scanned. */
@SpringBootTest
class CoreIocApplicationTests {

    @Autowired NotificationService notifications;
    @Autowired CartService carts;
    @Autowired OrderService orders;
    @Autowired OrderListeners listeners;
    @Autowired ApplicationContext context;

    @Test
    void resolvesAmbiguity_byPrimaryQualifierListAndMap() {
        assertThat(notifications.notifyDefault("hi")).isEqualTo("email:hi");
        assertThat(notifications.notifySms("hi")).isEqualTo("sms:hi");
        assertThat(notifications.broadcast("hi")).containsExactly("email:hi", "sms:hi");
        assertThat(notifications.notifyVia("smsSender", "hi")).isEqualTo("sms:hi");
        assertThatThrownBy(() -> notifications.notifyVia("pigeon", "hi"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void prototypeInSingleton_isStaleUnlessLookedUpViaProvider() {
        assertThat(carts.staleCart()).isSameAs(carts.staleCart());
        assertThat(carts.newCart()).isNotSameAs(carts.newCart());
    }

    @Test
    void listenersRunSynchronouslyInOrder_andConditionFilters() {
        listeners.clear();
        String callerThread = Thread.currentThread().getName();

        orders.placeOrder("A1", 500);
        orders.placeOrder("B2", 250_000);

        assertThat(listeners.handled()).containsExactly(
                "stock:A1:" + callerThread,
                "stock:B2:" + callerThread,
                "fraud:B2");
    }

    @Test
    void beanRegistrar_registersBeansFromEnvironment() {
        assertThat(context.getBean(PaymentGateway.class).provider()).isEqualTo("stripe");
        assertThat(context.getBean(FeatureFlags.class).fastCheckout()).isTrue();
    }
}
