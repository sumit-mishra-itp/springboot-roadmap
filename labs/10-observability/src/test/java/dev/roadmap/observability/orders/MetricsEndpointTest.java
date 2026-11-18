package dev.roadmap.observability.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Boot 4: @AutoConfigureMetrics replaces Boot 3's @AutoConfigureObservability for metrics.
 * Without it, @SpringBootTest keeps only an in-memory registry and /actuator/prometheus does not exist
 * (see HealthProbesTest#prometheusEndpoint_isAbsentInTests_withoutAutoConfigureMetrics).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@AutoConfigureMetrics
class MetricsEndpointTest {

    private final RestTestClient client;
    private final MeterRegistry meterRegistry;

    MetricsEndpointTest(@Autowired RestTestClient client, @Autowired MeterRegistry meterRegistry) {
        this.client = client;
        this.meterRegistry = meterRegistry;
    }

    private void placeAndLookUp(String channel, String tier) {
        Order order = client.post().uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new OrderRequest(channel, tier, new BigDecimal("120.00")))
                .exchange()
                .expectStatus().isCreated()
                .returnResult(Order.class).getResponseBody();
        client.get().uri("/orders/{id}", order.id()).exchange().expectStatus().isOk();
    }

    @Test
    void prometheus_exposesEveryCustomMeterInPrometheusNaming() {
        placeAndLookUp("web", "gold");

        String scrape = client.get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .returnResult(String.class).getResponseBody();

        assertThat(scrape)
                // Counter: dots become underscores, "_total" suffix; common tag region from management.metrics.tags
                .contains("orders_placed_total{channel=\"web\",region=\"local\",suspicious=\"false\"} 1.0")
                // Timer with histogram + SLOs: base unit seconds, cumulative le buckets incl. the SLO boundaries
                .contains("orders_checkout_seconds_bucket{region=\"local\",le=\"0.1\"}")
                .contains("orders_checkout_seconds_bucket{region=\"local\",le=\"0.5\"}")
                .contains("orders_checkout_seconds_count{region=\"local\"}")
                // DistributionSummary: base unit appended, client-side percentiles as quantile series
                .contains("orders_amount_usd{region=\"local\",quantile=\"0.95\"}")
                .contains("orders_amount_usd_sum{region=\"local\"}")
                // Gauge
                .contains("orders_in_flight{region=\"local\"} 0.0")
                // @Timed + @Counted (MetricsAspectsAutoConfiguration)
                .contains("orders_lookup_seconds_count{")
                .contains("orders_lookup_calls_total{class=\"dev.roadmap.observability.orders.OrderService\",exception=\"none\",method=\"find\",region=\"local\",result=\"success\"}")
                // Observation API: a timer tagged with the LOW cardinality key only
                .contains("orders_pricing_seconds_count{error=\"none\",region=\"local\",tier=\"gold\"} 1")
                .contains("orders_pricing_active_seconds")
                // @Observed
                .contains("orders_fraud_check_seconds_count{")
                // Framework instrumentation: uri is the template, never the raw path (bounded cardinality)
                .contains("http_server_requests_seconds_count{")
                .contains("uri=\"/orders/{id}\"")
                // The high-cardinality key value never becomes a tag
                .doesNotContain("order_id");
    }

    @Test
    void unmatchedPathAndInvalidBody_keepTagValuesBounded() {
        client.get().uri("/no/such/path/123").exchange().expectStatus().isNotFound();
        client.post().uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"channel\":\"web\",\"amount\":10}")                       // tier missing
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);

        String scrape = client.get().uri("/actuator/prometheus").exchange()
                .returnResult(String.class).getResponseBody();
        assertThat(scrape)
                // MVC's static-resource handler matches /** first, so the template is "/**", not the raw path
                .contains("status=\"404\",uri=\"/**\"")
                .doesNotContain("/no/such/path");
    }

    @Test
    void observation_producedATimerAndALongTaskTimer() {
        placeAndLookUp("mobile", "silver");

        // Only one registry on the classpath is active (OTLP export is off), so Boot skips the composite wrapper
        assertThat(meterRegistry).isInstanceOf(PrometheusMeterRegistry.class);
        assertThat(meterRegistry.get("orders.pricing").tag("tier", "silver").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("orders.pricing.active").longTaskTimer().activeTasks()).isZero();
        assertThat(meterRegistry.get("orders.pricing").timer().getId().getTag("order.id")).isNull();
    }
}
