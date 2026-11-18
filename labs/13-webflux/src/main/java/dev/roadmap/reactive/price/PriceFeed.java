package dev.roadmap.reactive.price;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;

import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * Fans newly recorded prices out to every connected SSE client.
 * <p>
 * {@code directBestEffort()} is a hot sink with no replay. One slow subscriber never holds back the others
 * (a plain {@code publish()} or {@code share()} paces everyone to the slowest subscriber). Each client then gets
 * its own bounded buffer, which drops that client's oldest prices when it can't keep up.
 */
@Component
public class PriceFeed {

    private static final Logger log = LoggerFactory.getLogger(PriceFeed.class);
    private static final int PER_CLIENT_BUFFER = 256;
    private static final Duration HEARTBEAT = Duration.ofSeconds(15);

    private final Sinks.Many<PriceResponse> sink = Sinks.many().multicast().directBestEffort();

    void publish(PriceResponse price) {
        // emitNext drops the value when nobody listens, and spins briefly if two threads emit at the same moment.
        sink.emitNext(price, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
    }

    Flux<ServerSentEvent<PriceResponse>> stream(String symbol) {
        Flux<ServerSentEvent<PriceResponse>> prices = sink.asFlux()
                .filter(price -> price.symbol().equals(symbol))
                .onBackpressureBuffer(PER_CLIENT_BUFFER,
                        dropped -> log.debug("Slow SSE client, dropped price {}", dropped.id()),
                        BufferOverflowStrategy.DROP_OLDEST)
                .map(price -> ServerSentEvent.builder(price).id(String.valueOf(price.id())).event("price").build());

        // The first heartbeat fires at once. It commits the response headers and stops proxies closing an idle stream.
        Flux<ServerSentEvent<PriceResponse>> heartbeats = Flux.interval(Duration.ZERO, HEARTBEAT)
                .map(tick -> ServerSentEvent.<PriceResponse>builder().comment("heartbeat").build());

        // merge subscribes to the prices first, so no price is missed before the first heartbeat goes out.
        return Flux.merge(prices, heartbeats);
    }
}
