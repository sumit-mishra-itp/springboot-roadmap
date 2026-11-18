package dev.roadmap.observability.orders;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;

/** Plain JUnit: TestObservationRegistry records observations without any Spring context, meter registry or tracer. */
class PricingObservationTest {

    private final TestObservationRegistry registry = TestObservationRegistry.create();
    private final PricingService pricing = new PricingService(registry);

    @Test
    void price_isObservedWithLowAndHighCardinalityKeyValues() {
        BigDecimal total = pricing.price("order-42", "gold", new BigDecimal("100"));

        assertThat(total).isEqualByComparingTo("90");
        assertThat(registry)
                .hasObservationWithNameEqualTo("orders.pricing")
                .that()
                .hasContextualNameEqualTo("price-order")
                .hasLowCardinalityKeyValue("tier", "gold")
                .hasHighCardinalityKeyValue("order.id", "order-42")
                .hasBeenStarted()
                .hasBeenStopped();
    }
}
