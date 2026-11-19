package dev.roadmap.shop.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** One order with its lines. Map it inside the transaction, or from an order loaded with its lines. */
public record OrderResponse(long id, BigDecimal total, Instant createdAt, List<Line> lines) {

    public record Line(long productId, int quantity, BigDecimal unitPrice) {
    }

    static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getTotal(), order.getCreatedAt(), order.getLines().stream()
                .map(line -> new Line(line.getProductId(), line.getQuantity(), line.getUnitPrice()))
                .toList());
    }
}
