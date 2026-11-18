package dev.roadmap.observability.health;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health of an external dependency (think: payment gateway or database).
 * Its id is the bean name minus the "HealthIndicator" suffix: {@code downstream}.
 * It is wired into the readiness group only (see application.properties). A broken
 * dependency should take the pod out of the load balancer, never get it restarted.
 */
@Component
public class DownstreamHealthIndicator implements HealthIndicator {

    // ponytail: a flag stands in for a real ping; swap in a cheap, timeout-bounded call to the dependency
    private final AtomicBoolean reachable = new AtomicBoolean(true);

    @Override
    public Health health() {
        if (!reachable.get()) {
            return Health.down().withDetail("gateway", "payments").withDetail("reason", "connection refused").build();
        }
        return Health.up().withDetail("gateway", "payments").build();
    }

    public void markReachable(boolean value) {
        reachable.set(value);
    }
}
