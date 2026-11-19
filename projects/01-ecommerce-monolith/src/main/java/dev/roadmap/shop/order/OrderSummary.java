package dev.roadmap.shop.order;

import java.math.BigDecimal;
import java.time.Instant;

/** A row of "my orders". No lines, so listing a page never touches the lines collection (see OrderRepository). */
public record OrderSummary(long id, BigDecimal total, Instant createdAt) {

    static OrderSummary from(Order order) {
        return new OrderSummary(order.getId(), order.getTotal(), order.getCreatedAt());
    }
}
