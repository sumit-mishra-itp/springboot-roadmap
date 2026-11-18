package dev.roadmap.reactive.fx;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Wraps a blocking API the reactive way: run it lazily, on boundedElastic, never on a Netty event-loop thread. */
@Service
public class FxRateService {

    private static final Map<String, BigDecimal> USD_RATES = Map.of(
            "USD", BigDecimal.ONE, "EUR", new BigDecimal("0.92"), "INR", new BigDecimal("83.10"));

    public Mono<BigDecimal> usdTo(String currency) {
        return Mono.fromCallable(() -> blockingLookup(currency))   // a null result becomes an empty Mono
                .subscribeOn(Schedulers.boundedElastic())
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported currency " + currency)));
    }

    /**
     * Stands in for JDBC, a legacy SOAP client or file I/O: it parks the calling thread.
     * BlockHound would throw if this ran on a non-blocking (event-loop or parallel) thread.
     */
    BigDecimal blockingLookup(String currency) throws InterruptedException {
        Thread.sleep(50);
        return USD_RATES.get(currency);
    }
}
