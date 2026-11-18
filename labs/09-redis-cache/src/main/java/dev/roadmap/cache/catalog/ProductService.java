package dev.roadmap.cache.catalog;

import static dev.roadmap.cache.config.CacheNames.PRODUCTS;
import static dev.roadmap.cache.config.CacheNames.QUOTES;
import static dev.roadmap.cache.config.CacheNames.SKUS;
import static dev.roadmap.cache.config.CacheNames.SKUS_NEGATIVE;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

/**
 * Cache-aside through annotations. The CacheInterceptor (an AOP proxy around this bean) checks Redis
 * first and only runs the method body on a miss. Each method shows one attribute.
 */
@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    /**
     * sync = true: concurrent misses for the same key load once, the others wait for the value.
     * sync cannot be combined with unless, a second cache, or another cache annotation on the method.
     */
    @Cacheable(cacheNames = PRODUCTS, key = "#id", sync = true)
    public Product findById(long id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    /** unless runs AFTER the call against #result: a miss (null) is not cached, so every unknown SKU hits the DB. */
    @Cacheable(cacheNames = SKUS, key = "#sku.toUpperCase()", unless = "#result == null")
    public Product findBySku(String sku) {
        return repository.findBySku(sku).orElse(null);
    }

    /** Same lookup, but null IS cached (stored as NullValue) in a short-TTL cache: cache-penetration protection. */
    @Cacheable(cacheNames = SKUS_NEGATIVE, key = "#sku.toUpperCase()")
    public Product findBySkuCachingMisses(String sku) {
        return repository.findBySku(sku).orElse(null);
    }

    /**
     * No key attribute and two parameters, so the default KeyGenerator builds SimpleKey [id,currency].
     * condition runs BEFORE the call: other currencies bypass the cache entirely.
     */
    @Cacheable(cacheNames = QUOTES, condition = "#currency == 'EUR' or #currency == 'USD'")
    public PriceQuote quote(long id, String currency) {
        Product product = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        long rateBasisPoints = "EUR".equals(currency) ? 9_200 : 10_000;
        return new PriceQuote(id, currency, product.priceCents() * rateBasisPoints / 10_000);
    }

    /** Write to the DB, then delete the cache entry (delete-after-write). The next read reloads it. */
    @CacheEvict(cacheNames = PRODUCTS, key = "#id")
    public Product updatePrice(long id, long priceCents) {
        Product current = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        return repository.save(current.withPrice(priceCents));
    }

    /** @Caching groups operations: overwrite the products entry with the result and drop the SKU-index entry. */
    @Caching(
            put = @CachePut(cacheNames = PRODUCTS, key = "#id"),
            evict = @CacheEvict(cacheNames = SKUS, key = "#result.sku()"))
    public Product rename(long id, String name) {
        Product current = repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        return repository.save(current.withName(name));
    }

    /**
     * The self-invocation trap: findById(...) here is this.findById(...), a plain Java call on the target
     * object. It never passes through the caching proxy, so it always hits the repository.
     */
    public String priceLabel(long id) {
        Product product = findById(id);
        return product.name() + ": " + product.priceCents() / 100.0;
    }
}
