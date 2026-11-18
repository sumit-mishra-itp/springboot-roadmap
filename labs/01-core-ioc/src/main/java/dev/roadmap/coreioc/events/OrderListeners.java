package dev.roadmap.coreioc.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Listeners run synchronously on the publisher's thread by default, in @Order order.
 * An exception here propagates back to publishEvent() unless the listener is @Async.
 */
@Component
public class OrderListeners {

    private final List<String> handled = new CopyOnWriteArrayList<>();

    @EventListener
    @Order(1)
    void reserveStock(OrderPlaced event) {
        handled.add("stock:" + event.orderId() + ":" + Thread.currentThread().getName());
    }

    @EventListener(condition = "#event.amountCents() > 100000") // SpEL filter: only big orders
    @Order(2)
    void flagForFraudReview(OrderPlaced event) {
        handled.add("fraud:" + event.orderId());
    }

    public List<String> handled() {
        return List.copyOf(handled);
    }

    public void clear() {
        handled.clear();
    }
}
