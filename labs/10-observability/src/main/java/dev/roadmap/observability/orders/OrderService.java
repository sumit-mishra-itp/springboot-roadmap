package dev.roadmap.observability.orders;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

/** Every raw Micrometer meter type in one place: Counter, Timer, Gauge and DistributionSummary. */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final MeterRegistry meterRegistry;
    private final PricingService pricing;
    private final FraudCheck fraudCheck;
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    // A gauge holds only a WEAK reference to its state object. Keep a strong one in a field,
    // or the gauge silently reports NaN after the next GC.
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Timer checkoutTimer;
    private final DistributionSummary amountSummary;

    public OrderService(MeterRegistry meterRegistry, PricingService pricing, FraudCheck fraudCheck) {
        this.meterRegistry = meterRegistry;
        this.pricing = pricing;
        this.fraudCheck = fraudCheck;
        Gauge.builder("orders.in.flight", inFlight, AtomicInteger::get)
                .description("Orders currently being placed")
                .register(meterRegistry);
        this.checkoutTimer = Timer.builder("orders.checkout")
                .description("Time to place an order")
                .publishPercentileHistogram()                                           // server side: _bucket series, aggregatable
                .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(500)) // guarantees le="0.1" and le="0.5" buckets
                .register(meterRegistry);
        this.amountSummary = DistributionSummary.builder("orders.amount")
                .baseUnit("usd")
                .description("Order amount")
                .publishPercentiles(0.5, 0.95)                                          // client side: quantile series, NOT aggregatable
                .register(meterRegistry);
    }

    public Order place(OrderRequest request) {
        return checkoutTimer.record(() -> doPlace(request));
    }

    private Order doPlace(OrderRequest request) {
        inFlight.incrementAndGet();
        try {
            String id = UUID.randomUUID().toString();
            boolean suspicious = fraudCheck.isSuspicious(request.amount());
            BigDecimal total = pricing.price(id, request.tier(), request.amount());
            // The channel value comes from the client, so CardinalityGuard caps how many values it may take.
            Counter.builder("orders.placed")
                    .tag("channel", request.channel())
                    .tag("suspicious", String.valueOf(suspicious))
                    .register(meterRegistry)
                    .increment();
            amountSummary.record(request.amount().doubleValue());
            Order order = new Order(id, request.channel(), request.amount(), total);
            orders.put(id, order);
            log.info("Order placed id={} channel={} total={}", id, request.channel(), total);
            return order;
        } finally {
            inFlight.decrementAndGet();
        }
    }

    @Timed(value = "orders.lookup", description = "Order lookups")
    @Counted(value = "orders.lookup.calls")
    public Optional<Order> find(String id) {
        return Optional.ofNullable(orders.get(id));
    }
}
