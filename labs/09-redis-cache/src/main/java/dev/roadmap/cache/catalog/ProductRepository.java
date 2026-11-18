package dev.roadmap.cache.catalog;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Repository;

/**
 * Stands in for a slow Postgres table. Every method sleeps LATENCY and bumps a call counter,
 * so tests can prove whether a call reached the "database" or was served from Redis.
 * ponytail: in-memory map. The lesson is the cache in front of the store, not the store; swap in JPA for a real app.
 */
@Repository
public class ProductRepository {

    static final Duration LATENCY = Duration.ofMillis(200);

    private final Map<Long, Product> rows = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();

    public ProductRepository() {
        reset();
    }

    public Optional<Product> findById(long id) {
        simulateRoundTrip();
        return Optional.ofNullable(rows.get(id));
    }

    public Optional<Product> findBySku(String sku) {
        simulateRoundTrip();
        return rows.values().stream().filter(p -> p.sku().equalsIgnoreCase(sku)).findFirst();
    }

    public Product save(Product product) {
        simulateRoundTrip();
        rows.put(product.id(), product);
        return product;
    }

    public int calls() {
        return calls.get();
    }

    /** Test hook: restore the seed data and zero the counter. */
    public void reset() {
        rows.clear();
        rows.put(1L, new Product(1, "BOOK-001", "Effective Java", "books", 4_500));
        rows.put(2L, new Product(2, "BOOK-002", "Java Concurrency in Practice", "books", 5_200));
        rows.put(3L, new Product(3, "MUG-001", "Duke mug", "merch", 1_200));
        calls.set(0);
    }

    private void simulateRoundTrip() {
        calls.incrementAndGet();
        try {
            Thread.sleep(LATENCY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying", e);
        }
    }
}
