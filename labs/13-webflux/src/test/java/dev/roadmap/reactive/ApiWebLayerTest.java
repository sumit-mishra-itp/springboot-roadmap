package dev.roadmap.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.reactor.autoconfigure.ReactorAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import dev.roadmap.reactive.fx.FxRateService;
import dev.roadmap.reactive.price.PriceResponse;
import dev.roadmap.reactive.price.PriceService;
import dev.roadmap.reactive.quote.Quote;
import dev.roadmap.reactive.quote.QuoteClient;
import dev.roadmap.reactive.quote.QuoteHandler;
import dev.roadmap.reactive.quote.QuoteRoutes;
import dev.roadmap.reactive.web.RequestIdFilter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * WebFlux slice: controllers, WebFilters and WebExceptionHandlers are loaded, services are mocks.
 * RouterFunction beans live in a @Configuration, which the slice does not scan, hence the @Import.
 * The slice also omits ReactorAutoConfiguration, so without the import the MDC test sees an empty requestId.
 */
@WebFluxTest
@ImportAutoConfiguration(ReactorAutoConfiguration.class)   // the slice leaves it out, and it is what applies spring.reactor.context-propagation=auto
@ExtendWith(OutputCaptureExtension.class)
@Import({QuoteRoutes.class, QuoteHandler.class})
class ApiWebLayerTest {

    @Autowired WebTestClient client;

    @MockitoBean PriceService prices;
    @MockitoBean QuoteClient quotes;
    @MockitoBean FxRateService fx;

    private static final PriceResponse SAVED = new PriceResponse(7L, "ACME", new BigDecimal("101.25"), Instant.parse("2026-11-18T10:00:00Z"));

    // ---------- annotated controller ----------

    @Test
    void postPrice_returns201WithLocation() {
        given(prices.record(any())).willReturn(Mono.just(SAVED));

        client.post().uri("/prices").bodyValue(Map.of("symbol", "ACME", "amount", 101.25))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().location("/prices/7")
                .expectBody().jsonPath("$.amount").isEqualTo(101.25);
    }

    @Test
    void invalidBody_isA400ProblemDetail_listingEachField_andNeverReachesTheService() {
        client.post().uri("/prices").bodyValue(Map.of("symbol", "acme", "amount", -1))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.instance").isEqualTo("/prices")
                .jsonPath("$.errors.length()").isEqualTo(2);
        then(prices).should(never()).record(any());
    }

    @Test
    void missingPrice_isA404ProblemDetail() {
        given(prices.find(42)).willReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Price 42 not found")));

        client.get().uri("/prices/42").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.detail").isEqualTo("Price 42 not found");
    }

    @Test
    void pageSizeAbove100_failsMethodValidation() {
        client.get().uri("/prices?symbol=ACME&size=500").exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    // ---------- functional endpoint ----------

    @Test
    void functionalRoute_zipsQuoteAndFxRate() {
        given(quotes.fetch("ACME")).willReturn(Mono.just(new Quote("ACME", new BigDecimal("100"), false)));
        given(fx.usdTo("EUR")).willReturn(Mono.just(new BigDecimal("0.92")));

        client.get().uri("/quotes/ACME?currency=EUR").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.currency").isEqualTo("EUR")
                .jsonPath("$.price").isEqualTo(92.0);
    }

    @Test
    void functionalRouteError_isRenderedByTheGlobalWebExceptionHandler() {
        given(quotes.fetch("NOPE")).willReturn(Mono.error(new dev.roadmap.reactive.quote.UnknownSymbolException("NOPE")));
        given(fx.usdTo("USD")).willReturn(Mono.just(BigDecimal.ONE));

        client.get().uri("/quotes/NOPE").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.detail").isEqualTo("Unknown symbol NOPE")
                .jsonPath("$.instance").isEqualTo("/quotes/NOPE");
    }

    // ---------- WebFilter ----------

    @Test
    void requestId_reachesTheMdc_inLogLinesWrittenOnAnotherThread(CapturedOutput output) {
        given(quotes.fetch("ACME")).willReturn(Mono.just(new Quote("ACME", new BigDecimal("100"), false)));
        given(fx.usdTo("USD")).willReturn(Mono.just(BigDecimal.ONE).publishOn(Schedulers.boundedElastic()));   // force a thread hop

        client.get().uri("/quotes/ACME").header(RequestIdFilter.HEADER, "abc-123").exchange().expectStatus().isOk();

        assertThat(output).contains("[rid=abc-123]").contains("Quote ACME in USD");
    }

    @Test
    void requestId_isEchoed_whenSafe_andReplaced_whenNot() {
        given(prices.find(7)).willReturn(Mono.just(SAVED));

        client.get().uri("/prices/7").header(RequestIdFilter.HEADER, "abc-123").exchange()
                .expectHeader().valueEquals(RequestIdFilter.HEADER, "abc-123");

        client.get().uri("/prices/7").header(RequestIdFilter.HEADER, "evil\nlog line").exchange()
                .expectHeader().valueMatches(RequestIdFilter.HEADER, "[0-9a-f-]{36}");
    }
}
