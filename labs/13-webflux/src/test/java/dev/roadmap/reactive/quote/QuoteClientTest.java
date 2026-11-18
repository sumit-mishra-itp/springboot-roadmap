package dev.roadmap.reactive.quote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.test.StepVerifier;

/**
 * WebClient resilience against a real (JDK built-in) HTTP server that we can make slow or failing.
 * No Spring context: QuoteClient is built with {@code new}.
 */
class QuoteClientTest {

    private static final String ACME = "{\"symbol\":\"ACME\",\"price\":101.25}";

    private final AtomicInteger calls = new AtomicInteger();
    private HttpServer server;
    private volatile IntFunction<Reply> behaviour;   // call number -> reply

    private record Reply(int status, String body, long delayMillis) { }

    @BeforeEach
    void startDownstream() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());   // a slow reply must not block the next attempt
        server.createContext("/quotes", this::serve);
        server.start();
    }

    @AfterEach
    void stopDownstream() {
        server.stop(0);
    }

    private void serve(HttpExchange exchange) throws IOException {
        Reply reply = behaviour.apply(calls.incrementAndGet());
        try {
            Thread.sleep(reply.delayMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private QuoteClient client() {
        var properties = new QuoteClientProperties(URI.create("http://localhost:" + server.getAddress().getPort()),
                Duration.ofMillis(200), 2, Duration.ofMillis(10));
        return new QuoteClient(WebClient.builder(), properties);
    }

    @Test
    void retriesTransient5xx_thenSucceeds() {
        behaviour = call -> call < 3 ? new Reply(503, "", 0) : new Reply(200, ACME, 0);

        StepVerifier.create(client().fetch("ACME"))
                .expectNext(new Quote("ACME", new BigDecimal("101.25"), false))
                .verifyComplete();
        assertThat(calls).hasValue(3);
    }

    @Test
    void notFound_isMappedToADomainError_andNeverRetried() {
        behaviour = call -> new Reply(404, "", 0);

        StepVerifier.create(client().fetch("NOPE")).expectError(UnknownSymbolException.class).verify();
        assertThat(calls).hasValue(1);
    }

    @Test
    void slowDownstream_timesOutEachAttempt_thenServesTheLastKnownQuoteAsStale() {
        QuoteClient client = client();
        behaviour = call -> new Reply(200, ACME, 0);
        StepVerifier.create(client.fetch("ACME")).expectNextMatches(q -> !q.stale()).verifyComplete();

        behaviour = call -> new Reply(200, ACME, 1_000);   // 5x the 200 ms timeout

        StepVerifier.create(client.fetch("ACME"))
                .expectNext(new Quote("ACME", new BigDecimal("101.25"), true))
                .verifyComplete();
        assertThat(calls).hasValue(1 + 3);   // the first call, then 1 attempt + 2 retries
    }

    @Test
    void downstreamDown_andNothingCached_fails503() {
        behaviour = call -> new Reply(500, "", 0);

        StepVerifier.create(client().fetch("ACME"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(503))
                .verify();
    }
}
