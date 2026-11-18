package dev.roadmap.reactive.price;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PriceService {

    private final PriceRepository repository;
    private final PriceFeed feed;
    private final TransactionalOperator transactional;

    public PriceService(PriceRepository repository, PriceFeed feed, TransactionalOperator transactional) {
        this.repository = repository;
        this.feed = feed;
        this.transactional = transactional;
    }

    public Mono<PriceResponse> record(PriceRequest request) {
        return repository.save(Price.newPrice(request.symbol(), request.amount()))
                .map(PriceResponse::from)
                .doOnNext(feed::publish);
    }

    public Mono<PriceResponse> find(long id) {
        return repository.findById(id)
                .map(PriceResponse::from)
                // Mono.error(Supplier): the exception is only built if the Mono really is empty.
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Price " + id + " not found")));
    }

    public Flux<PriceResponse> history(String symbol, int page, int size) {
        return repository.findBySymbolOrderByRecordedAtDesc(symbol, PageRequest.of(page, size))
                .map(PriceResponse::from);
    }

    public Flux<ServerSentEvent<PriceResponse>> stream(String symbol) {
        return feed.stream(symbol);
    }

    /**
     * All-or-nothing import. TransactionalOperator binds the R2DBC transaction (and its one connection) to the
     * Reactor Context, not to a ThreadLocal, so the transaction survives the pipeline's thread hops.
     * concatMap, not flatMap: one connection runs one statement at a time.
     */
    public Flux<PriceResponse> importAll(List<PriceRequest> batch) {
        return Flux.fromIterable(batch)
                .map(request -> Price.newPrice(request.symbol(), request.amount()))
                .concatMap(repository::save)
                .map(PriceResponse::from)
                .as(transactional::transactional);
    }
}
