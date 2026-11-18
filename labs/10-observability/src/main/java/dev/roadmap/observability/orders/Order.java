package dev.roadmap.observability.orders;

import java.math.BigDecimal;

public record Order(String id, String channel, BigDecimal amount, BigDecimal total) {
}
