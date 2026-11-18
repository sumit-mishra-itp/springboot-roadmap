package dev.roadmap.reactive.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;

import dev.roadmap.reactive.TestcontainersConfiguration;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Real Netty server + real Postgres (Testcontainers). @ServiceConnection gives Spring BOTH an R2DBC
 * ConnectionFactory (for the app) and JDBC details (for Flyway, which has no R2DBC support).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestcontainersConfiguration.class)
class PriceIntegrationTest {

    @Autowired PriceRepository repository;
    @Autowired PriceService service;
    @Autowired WebTestClient client;
    @Autowired ObjectProvider<DataSource> dataSources;

    @BeforeEach
    void cleanTable() {
        repository.deleteAll().block();
    }

    @Test
    void flywaySchema_andDerivedQuery_pageBySymbolNewestFirst_withoutAnyDataSource() {
        Instant now = Instant.parse("2026-11-18T10:00:00Z");
        repository.saveAll(List.of(
                new Price(null, "ACME", new BigDecimal("1.00"), now.minusSeconds(20)),
                new Price(null, "ACME", new BigDecimal("2.00"), now.minusSeconds(10)),
                new Price(null, "ACME", new BigDecimal("3.00"), now),
                new Price(null, "OTHER", new BigDecimal("9.00"), now))).blockLast();

        StepVerifier.create(repository.findBySymbolOrderByRecordedAtDesc("ACME", PageRequest.of(0, 2)).map(Price::amount))
                .expectNext(new BigDecimal("3.0000"), new BigDecimal("2.0000"))   // numeric(19,4) scale comes back
                .verifyComplete();
        StepVerifier.create(repository.findBySymbolOrderByRecordedAtDesc("ACME", PageRequest.of(1, 2)).map(Price::amount))
                .expectNext(new BigDecimal("1.0000"))
                .verifyComplete();
        assertThat(dataSources.getIfAvailable()).isNull();   // Flyway used JDBC, yet no DataSource bean (no Hikari pool) exists
    }

    @Test
    void savingARecord_returnsANewInstanceCarryingTheGeneratedId() {
        Price unsaved = Price.newPrice("ACME", BigDecimal.TEN);

        Price saved = repository.save(unsaved).block();

        assertThat(unsaved.id()).isNull();
        assertThat(saved.id()).isNotNull();
    }

    @Test
    void postThenGet_overRealHttp() {
        PriceResponse created = client.post().uri("/prices")
                .bodyValue(new PriceRequest("ACME", new BigDecimal("42.5")))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(PriceResponse.class).returnResult().getResponseBody();

        client.get().uri("/prices/{id}", created.id()).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.symbol").isEqualTo("ACME");
    }

    @Test
    void transactionalOperator_rollsBackTheWholeBatch_whenOneRowFails() {
        List<PriceRequest> batch = List.of(
                new PriceRequest("ACME", BigDecimal.ONE),
                new PriceRequest("ACME", new BigDecimal("-5")));   // passes Java (no @Valid here), fails the CHECK constraint

        StepVerifier.create(service.importAll(batch)).expectNextCount(1).expectError(DataIntegrityViolationException.class).verify();

        StepVerifier.create(repository.count()).expectNext(0L).verifyComplete();   // the first INSERT was rolled back
    }

    @Test
    void cancellingMidTransaction_rollsBackWhatWasAlreadyInserted() {
        List<PriceRequest> batch = List.of(
                new PriceRequest("ACME", BigDecimal.ONE), new PriceRequest("ACME", BigDecimal.TWO), new PriceRequest("ACME", BigDecimal.TEN));

        StepVerifier.create(service.importAll(batch), 1).expectNextCount(1).thenCancel().verify();

        // "during": the count must STAY 0, so a late commit after the cancel would fail the test.
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3))
                .until(() -> repository.count().block() == 0L);
    }

    @Test
    void sseStream_pushesNewPricesToConnectedClients() {
        Flux<ServerSentEvent<PriceResponse>> events = client.get().uri("/prices/stream?symbol=SSE")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()   // returns once headers arrive: the immediate heartbeat committed them
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<PriceResponse>>() { })
                .getResponseBody();

        StepVerifier.create(events.filter(event -> event.data() != null))   // skip heartbeat comments
                .then(() -> service.record(new PriceRequest("SSE", new BigDecimal("7.5"))).block())
                .assertNext(event -> {
                    assertThat(event.event()).isEqualTo("price");
                    assertThat(event.data().amount()).isEqualByComparingTo("7.5");
                })
                .thenCancel()   // an SSE stream never completes on its own
                .verify(Duration.ofSeconds(10));
    }
}
