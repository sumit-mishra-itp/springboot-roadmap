package dev.roadmap.observability.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import dev.roadmap.observability.orders.OrderRequest;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Boot 4: @AutoConfigureTracing replaces Boot 3's @AutoConfigureObservability for tracing.
 * It turns span export back on, so the InMemorySpanExporter bean below receives finished spans.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
        "logging.structured.format.console=ecs",
        "management.opentelemetry.tracing.export.schedule-delay=50ms"
})
@AutoConfigureRestTestClient
@AutoConfigureTracing
@ExtendWith(OutputCaptureExtension.class)
class TracePropagationTest {

    // A W3C traceparent as an upstream service (or the browser, or the gateway) would send it
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String UPSTREAM_SPAN_ID = "00f067aa0ba902b7";
    private static final String TRACEPARENT = "00-" + TRACE_ID + "-" + UPSTREAM_SPAN_ID + "-01";

    @TestConfiguration(proxyBeanMethods = false)
    static class SpanCapture {
        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();   // Boot adds every SpanExporter bean to the SdkTracerProvider
        }
    }

    private final RestTestClient client;
    private final InMemorySpanExporter spans;

    TracePropagationTest(@Autowired RestTestClient client, @Autowired InMemorySpanExporter spans) {
        this.client = client;
        this.spans = spans;
    }

    @BeforeEach
    void clearSpans() {
        spans.reset();
    }

    @Test
    void incomingTraceparent_isContinued_andPropagatedOnTheOutgoingRestClientCall() {
        TraceController.TraceReport report = client.get().uri("/trace/outbound")
                .header("traceparent", TRACEPARENT)
                .exchange()
                .expectStatus().isOk()
                .returnResult(TraceController.TraceReport.class).getResponseBody();

        // Same trace id end to end
        assertThat(report.traceId()).isEqualTo(TRACE_ID);
        // version-traceid-parentspanid-flags: same trace, NEW parent (our client span), still sampled
        String[] echoed = report.echoedTraceparent().split("-");
        assertThat(echoed).hasSize(4);
        assertThat(echoed[0]).isEqualTo("00");
        assertThat(echoed[1]).isEqualTo(TRACE_ID);
        assertThat(echoed[2]).hasSize(16).isNotEqualTo(UPSTREAM_SPAN_ID);
        assertThat(echoed[3]).isEqualTo("01");

        List<SpanData> trace = awaitSpans(3);
        assertThat(trace).extracting(SpanData::getTraceId).containsOnly(TRACE_ID);
        assertThat(trace).extracting(SpanData::getKind)
                .containsExactlyInAnyOrder(SpanKind.SERVER, SpanKind.CLIENT, SpanKind.SERVER);
        SpanData clientSpan = trace.stream().filter(s -> s.getKind() == SpanKind.CLIENT).findFirst().orElseThrow();
        // The span id the callee received as its parent IS the client span
        assertThat(clientSpan.getSpanId()).isEqualTo(echoed[2]);
    }

    @Test
    void noIncomingHeader_startsANewTrace() {
        TraceController.TraceReport report = client.get().uri("/trace/outbound").exchange()
                .expectStatus().isOk()
                .returnResult(TraceController.TraceReport.class).getResponseBody();

        assertThat(report.traceId()).hasSize(32).isNotEqualTo(TRACE_ID);
        assertThat(report.echoedTraceparent()).startsWith("00-" + report.traceId() + "-");
    }

    @Test
    void unsampledTraceparent_isStillPropagatedAndLogged_withFlag00(CapturedOutput output) {
        String unsampledTraceId = "0af7651916cd43dd8448eb211c80319c";
        TraceController.TraceReport report = client.get().uri("/trace/outbound")
                .header("traceparent", "00-" + unsampledTraceId + "-b7ad6b7169203331-00")
                .exchange()
                .expectStatus().isOk()
                .returnResult(TraceController.TraceReport.class).getResponseBody();

        // The upstream decided "don't record": we honour it (parent-based sampler) but keep the ids flowing
        assertThat(report.traceId()).isEqualTo(unsampledTraceId);
        assertThat(report.echoedTraceparent()).startsWith("00-" + unsampledTraceId + "-").endsWith("-00");
        assertThat(output.getOut()).contains("\"traceId\":\"" + unsampledTraceId + "\"");
    }

    @Test
    void customObservation_becomesASpan_withHighCardinalityAttribute() {
        client.post().uri("/orders")
                .header("traceparent", TRACEPARENT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new OrderRequest("web", "gold", new BigDecimal("50")))
                .exchange()
                .expectStatus().isCreated();

        List<SpanData> trace = awaitSpans(3);
        SpanData pricing = trace.stream().filter(s -> s.getName().equals("price-order")).findFirst().orElseThrow();
        assertThat(pricing.getAttributes().get(AttributeKey.stringKey("tier"))).isEqualTo("gold");
        assertThat(pricing.getAttributes().get(AttributeKey.stringKey("order.id"))).isNotBlank();
        assertThat(trace).extracting(SpanData::getName).contains("fraud-check");
    }

    @Test
    void structuredLogs_carryTheTraceAndSpanIds(CapturedOutput output) {
        client.get().uri("/trace/outbound").header("traceparent", TRACEPARENT).exchange().expectStatus().isOk();

        String line = output.getOut().lines()
                .filter(l -> l.contains("Calling /trace/echo on myself"))
                .findFirst().orElseThrow();
        assertThat(line).startsWith("{").contains("\"ecs\":{\"version\"")
                .contains("\"traceId\":\"" + TRACE_ID + "\"")
                .contains("\"spanId\":\"");
    }

    private List<SpanData> awaitSpans(int atLeast) {
        await().atMost(Duration.ofSeconds(5)).until(() -> spans.getFinishedSpanItems().size() >= atLeast);
        return spans.getFinishedSpanItems();
    }
}
