package dev.roadmap.cache.config;

/** Cache names used in annotations. Each one needs a TTL under app.cache.ttl, because the manager refuses unknown caches. */
public final class CacheNames {

    public static final String PRODUCTS = "products";
    public static final String SKUS = "skus";
    public static final String SKUS_NEGATIVE = "skus-negative";
    public static final String QUOTES = "quotes";

    private CacheNames() {
    }
}
