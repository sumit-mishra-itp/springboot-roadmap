package dev.roadmap.cache.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import dev.roadmap.cache.RedisIntegrationTest;

/** 8 threads miss the same cold key at the same instant (a hot product right after a deploy or an expiry). */
class StampedeTest extends RedisIntegrationTest {

    private static final int THREADS = 8;

    @Autowired
    ProductService service;

    @Autowired
    RedisConnectionFactory connectionFactory;

    @Test
    void syncTrue_withLockingWriter_collapsesConcurrentMissesIntoOneLoad() throws Exception {
        List<Object> results = runConcurrently(() -> service.findById(2));

        assertThat(results).hasSize(THREADS).allMatch(p -> p.equals(results.getFirst()));
        assertThat(repository.calls()).isEqualTo(1);
    }

    @Test
    void withoutSync_everyConcurrentMissHitsTheDatabase() throws Exception {
        runConcurrently(() -> service.findBySku("BOOK-002"));

        assertThat(repository.calls()).isGreaterThan(1);
    }

    /**
     * sync = true makes CacheInterceptor call Cache.get(key, valueLoader). On Spring Data Redis 4.x that is
     * NOT synchronized: with Boot's default non-locking writer, every concurrent miss still runs the loader.
     */
    @Test
    void cacheGetWithLoader_onBootsDefaultNonLockingWriter_doesNotCollapseMisses() throws Exception {
        Cache cache = RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory))
                .cacheDefaults(RedisCacheConfiguration.defaultCacheConfig().prefixCacheNameWith("nonlocking:"))
                .build()
                .getCache("products");
        AtomicInteger loads = new AtomicInteger();

        runConcurrently(() -> cache.get(2L, () -> {
            loads.incrementAndGet();
            Thread.sleep(200);
            return "loaded";
        }));

        assertThat(loads.get()).isGreaterThan(1);
    }

    /** The price of the locking writer: the lock is per cache, so a slow load for key 2 also stalls a HIT on key 1. */
    @Test
    void lockingWriter_aLoadInProgressBlocksHitsOnOtherKeysOfTheSameCache() throws Exception {
        service.findById(1);   // warm key 1
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<Product> slowMiss = pool.submit(() -> service.findById(2));   // holds products~lock for ~200ms
            Thread.sleep(50);

            long start = System.nanoTime();
            service.findById(1);   // a cache hit, yet it waits for the lock
            Duration hitLatency = Duration.ofNanos(System.nanoTime() - start);

            slowMiss.get();
            assertThat(hitLatency).isGreaterThan(Duration.ofMillis(100));
        }
    }

    private List<Object> runConcurrently(Callable<Object> call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<Object>> futures = IntStream.range(0, THREADS)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        return call.call();
                    }))
                    .toList();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }
}
