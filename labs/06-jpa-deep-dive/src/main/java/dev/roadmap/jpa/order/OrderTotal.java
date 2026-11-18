package dev.roadmap.jpa.order;

import java.math.BigDecimal;

/** Read model built by a JPQL constructor expression: no entity, no persistence context, no dirty checking. */
public record OrderTotal(Long orderId, BigDecimal total) {
}
