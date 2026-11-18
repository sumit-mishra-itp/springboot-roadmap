package dev.roadmap.reactive.quote;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import dev.roadmap.reactive.fx.FxRateService;
import reactor.core.publisher.Mono;

/** Handler functions for the functional-endpoint style: {@code ServerRequest -> Mono of ServerResponse}. */
@Component
public class QuoteHandler {

    private static final Logger log = LoggerFactory.getLogger(QuoteHandler.class);

    private final QuoteClient quotes;
    private final FxRateService fx;

    public QuoteHandler(QuoteClient quotes, FxRateService fx) {
        this.quotes = quotes;
        this.fx = fx;
    }

    /** BFF-style fan-out: zip subscribes to both calls at once, so the latency is max(a, b), not a + b. */
    public Mono<ServerResponse> quote(ServerRequest request) {
        String symbol = request.pathVariable("symbol");
        String currency = request.queryParam("currency").orElse("USD");
        return Mono.zip(quotes.fetch(symbol), fx.usdTo(currency), (quote, rate) -> ConvertedQuote.of(quote, currency, rate))
                .doOnNext(converted -> log.info("Quote {} in {} stale={}", symbol, currency, converted.stale()))  // MDC has requestId here
                .flatMap(converted -> ServerResponse.ok().bodyValue(converted));
    }

    /** Plays the slow downstream, so {@code spring-boot:test-run} works with no other service. NOPE is unknown. */
    public Mono<ServerResponse> simulatedDownstream(ServerRequest request) {
        String symbol = request.pathVariable("symbol");
        if (symbol.equals("NOPE")) {
            return ServerResponse.notFound().build();
        }
        Duration latency = Duration.ofMillis(ThreadLocalRandom.current().nextLong(0, 1000));
        var quote = new QuoteClient.DownstreamQuote(symbol, BigDecimal.valueOf(ThreadLocalRandom.current().nextLong(1000, 20000), 2));
        return Mono.delay(latency).then(ServerResponse.ok().bodyValue(quote));   // a timer, not a sleeping thread
    }
}
