package dev.roadmap.observability.orders;

import java.math.BigDecimal;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.stereotype.Service;

/**
 * The Observation API. One instrumentation call produces a timer ({@code orders.pricing}),
 * a long-task timer ({@code orders.pricing.active}) and a span, because Boot registers both
 * a metrics handler and a tracing handler on the ObservationRegistry.
 */
@Service
public class PricingService {

    private final ObservationRegistry observationRegistry;

    public PricingService(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public BigDecimal price(String orderId, String tier, BigDecimal amount) {
        return Observation.createNotStarted("orders.pricing", observationRegistry)
                .contextualName("price-order")                    // span name
                .lowCardinalityKeyValue("tier", tier)             // metric tag AND span attribute
                .highCardinalityKeyValue("order.id", orderId)     // span attribute ONLY, never a metric tag
                .observe(() -> applyDiscount(tier, amount));
    }

    private BigDecimal applyDiscount(String tier, BigDecimal amount) {
        return "gold".equals(tier) ? amount.multiply(new BigDecimal("0.90")) : amount;
    }
}
