package dev.roadmap.aws.orders;

import java.util.UUID;

public record OrderPlaced(UUID orderId, String sku, int quantity) {
}
