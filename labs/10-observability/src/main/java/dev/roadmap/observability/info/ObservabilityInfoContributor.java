package dev.roadmap.observability.info;

import java.util.Map;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/** Adds an {@code observability} entry to /actuator/info so operators see how the app is wired. */
@Component
public class ObservabilityInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("observability", Map.of(
                "metrics", "prometheus scrape at /actuator/prometheus",
                "tracing", "opentelemetry, W3C traceparent",
                "logs", "ECS JSON on the console in the otel profile"));
    }
}
