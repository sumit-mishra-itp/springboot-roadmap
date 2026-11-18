package dev.roadmap.observability.orders;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class CardinalityGuardTest {

    @Test
    void channelTag_isCappedSoAClientCannotCreateUnboundedSeries() {
        var registry = new SimpleMeterRegistry();
        registry.config().meterFilter(CardinalityGuard.channelCap());

        for (int i = 0; i < 50; i++) {
            Counter.builder("orders.placed").tag("channel", "attacker-" + i).register(registry).increment();
        }

        assertThat(registry.find("orders.placed").counters()).hasSize(CardinalityGuard.MAX_CHANNELS);
    }
}
