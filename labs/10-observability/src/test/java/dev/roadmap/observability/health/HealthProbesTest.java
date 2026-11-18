package dev.roadmap.observability.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** No @AutoConfigureMetrics / @AutoConfigureTracing here: health, info and loggers need neither. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class HealthProbesTest {

    private final RestTestClient client;
    private final DownstreamHealthIndicator downstream;
    private final ApplicationContext context;

    HealthProbesTest(@Autowired RestTestClient client, @Autowired DownstreamHealthIndicator downstream,
                     @Autowired ApplicationContext context) {
        this.client = client;
        this.downstream = downstream;
        this.context = context;
    }

    @AfterEach
    void restore() {
        downstream.markReachable(true);
        AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Test
    void health_listsCustomIndicatorAndProbeGroups() {
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.components.downstream.status").isEqualTo("UP")
                .jsonPath("$.components.downstream.details.gateway").isEqualTo("payments")
                .jsonPath("$.components.livenessState.status").isEqualTo("UP")
                .jsonPath("$.components.readinessState.status").isEqualTo("UP")
                .jsonPath("$.groups[0]").isEqualTo("liveness")
                .jsonPath("$.groups[1]").isEqualTo("readiness");

        client.get().uri("/actuator/health/liveness").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.livenessState").exists()
                .jsonPath("$.components.downstream").doesNotExist();
        client.get().uri("/actuator/health/readiness").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.readinessState").exists()
                .jsonPath("$.components.downstream").exists();
    }

    @Test
    void downstreamDown_failsReadinessOnly_soKubernetesStopsRoutingButDoesNotRestart() {
        downstream.markReachable(false);

        client.get().uri("/actuator/health/readiness").exchange().expectStatus().isEqualTo(503);
        client.get().uri("/readyz").exchange().expectStatus().isEqualTo(503);
        client.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
        client.get().uri("/livez").exchange().expectStatus().isOk();
        // The root health aggregates every indicator, so it is DOWN too: never point a liveness probe at it.
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.components.downstream.details.reason").isEqualTo("connection refused");
    }

    @Test
    void refusingTraffic_failsReadiness_likeDuringGracefulShutdown() {
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);

        client.get().uri("/actuator/health/readiness").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.status").isEqualTo("OUT_OF_SERVICE");
        client.get().uri("/actuator/health/liveness").exchange().expectStatus().isOk();
    }

    @Test
    void info_showsCustomContributorEnvAndJava() {
        client.get().uri("/actuator/info").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.observability.tracing").isEqualTo("opentelemetry, W3C traceparent")
                .jsonPath("$.app.team").isEqualTo("platform")
                .jsonPath("$.java.version").exists();
    }

    @Test
    void loggers_changeLevelAtRuntime() {
        client.get().uri("/actuator/loggers/dev.roadmap.observability").exchange()
                .expectBody().jsonPath("$.effectiveLevel").isEqualTo("INFO");

        client.post().uri("/actuator/loggers/dev.roadmap.observability")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"configuredLevel\":\"DEBUG\"}")
                .exchange()
                .expectStatus().isNoContent();

        client.get().uri("/actuator/loggers/dev.roadmap.observability.orders").exchange()
                .expectBody().jsonPath("$.effectiveLevel").isEqualTo("DEBUG");   // children inherit

        client.post().uri("/actuator/loggers/dev.roadmap.observability")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"configuredLevel\":null}")                              // null resets to inherited
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void prometheusEndpoint_isAbsentInTests_withoutAutoConfigureMetrics() {
        client.get().uri("/actuator/prometheus").exchange().expectStatus().isNotFound();
    }

    @Test
    void withoutAutoConfigureTracing_spansAreLocalOnly_traceparentIsNeitherReadNorWritten() {
        String upstreamTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        client.get().uri("/trace/outbound")
                .header("traceparent", "00-" + upstreamTraceId + "-00f067aa0ba902b7-01")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                // A tracer still exists and starts a span with a fresh trace id ...
                .jsonPath("$.traceId").value(id -> assertThat((String) id).hasSize(32).isNotEqualTo(upstreamTraceId))
                // ... but the incoming header was ignored and nothing was injected into the outgoing call
                .jsonPath("$.echoedTraceparent").isEqualTo("absent");
    }
}
