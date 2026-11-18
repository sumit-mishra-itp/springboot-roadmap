package dev.roadmap.observability.orders;

import java.math.BigDecimal;

import io.micrometer.observation.annotation.Observed;

import org.springframework.stereotype.Component;

/**
 * Declarative twin of {@link PricingService}. {@code @Observed} is woven by Boot's ObservedAspect,
 * which needs aspectjweaver and management.observations.annotations.enabled=true.
 * It is a separate bean because the aspect is proxy-based: a self-call would skip it.
 */
@Component
public class FraudCheck {

    @Observed(name = "orders.fraud.check", contextualName = "fraud-check")
    public boolean isSuspicious(BigDecimal amount) {
        return amount.compareTo(new BigDecimal("10000")) > 0;
    }
}
