package dev.roadmap.reactive.quote;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Calls a slow, flaky downstream. Operator order is the whole lesson:
 * timeout (per attempt) → onErrorMap (404 becomes a domain error) → retryWhen (only transient errors)
 * → onErrorResume (fallback once retries are exhausted).
 */
@Component
public class QuoteClient {

    private static final Logger log = LoggerFactory.getLogger(QuoteClient.class);

    private final WebClient webClient;
    private final QuoteClientProperties properties;
    // ponytail: unbounded map, fine for a fixed symbol list. Use Caffeine with a TTL if symbols are open-ended.
    private final Map<String, Quote> lastKnown = new ConcurrentHashMap<>();

    public QuoteClient(WebClient.Builder builder, QuoteClientProperties properties) {
        this.webClient = builder.baseUrl(properties.baseUrl().toString()).build();
        this.properties = properties;
    }

    public Mono<Quote> fetch(String symbol) {
        return webClient.get().uri("/quotes/{symbol}", symbol)
                .retrieve()
                .bodyToMono(DownstreamQuote.class)
                .map(DownstreamQuote::toQuote)
                .timeout(properties.timeout())   // BEFORE retryWhen, so every attempt gets its own timeout
                .onErrorMap(WebClientResponseException.NotFound.class, notFound -> new UnknownSymbolException(symbol))
                .retryWhen(Retry.backoff(properties.maxRetries(), properties.firstBackoff()).filter(QuoteClient::isTransient))
                .doOnNext(quote -> lastKnown.put(symbol, quote))
                .onErrorResume(Exceptions::isRetryExhausted, exhausted -> fallback(symbol, exhausted));
    }

    private Mono<Quote> fallback(String symbol, Throwable cause) {
        Quote cached = lastKnown.get(symbol);
        if (cached == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Quote service unavailable for " + symbol, cause));
        }
        log.warn("Serving stale quote for {} after: {}", symbol, cause.getMessage());
        return Mono.just(cached.asStale());
    }

    /**
     * The downstream's wire contract, kept apart from our Quote. Decoding straight into Quote failed: the payload has no
     * "stale" field, and Jackson 3 fails on a missing primitive by default (FAIL_ON_NULL_FOR_PRIMITIVES is now on).
     */
    record DownstreamQuote(String symbol, BigDecimal price) {
        Quote toQuote() {
            return new Quote(symbol, price, false);
        }
    }

    private static boolean isTransient(Throwable error) {
        return error instanceof TimeoutException
                || error instanceof WebClientRequestException                       // connection refused, reset
                || error instanceof WebClientResponseException response && response.getStatusCode().is5xxServerError();
    }
}
