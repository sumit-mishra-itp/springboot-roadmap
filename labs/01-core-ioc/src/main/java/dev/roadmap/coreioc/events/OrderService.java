package dev.roadmap.coreioc.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** The publisher doesn't know who listens. That decoupling is the point of events. */
@Service
public class OrderService {

    private final ApplicationEventPublisher publisher;

    public OrderService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void placeOrder(String orderId, long amountCents) {
        publisher.publishEvent(new OrderPlaced(orderId, amountCents));
    }
}
