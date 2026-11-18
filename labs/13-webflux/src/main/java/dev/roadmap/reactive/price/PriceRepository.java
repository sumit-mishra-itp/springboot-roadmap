package dev.roadmap.reactive.price;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;

public interface PriceRepository extends ReactiveCrudRepository<Price, Long> {

    /** Derived query. The Pageable becomes LIMIT/OFFSET. Reactive repositories return Flux, not Page (no extra COUNT). */
    Flux<Price> findBySymbolOrderByRecordedAtDesc(String symbol, Pageable pageable);
}
