package dev.roadmap.cache.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.roadmap.cache.RedisIntegrationTest;

class CacheExpiryTest extends RedisIntegrationTest {

    @Autowired
    ProductService service;

    @Test
    void eachCacheGetsItsOwnTtl_plusUpToTenPercentJitter() {
        service.findById(1);
        service.quote(1, "EUR");

        long productTtl = redis.getExpire("catalog:products::1", TimeUnit.MILLISECONDS);
        long quoteTtl = redis.getExpire("catalog:quotes::SimpleKey [1, EUR]", TimeUnit.MILLISECONDS);

        assertThat(productTtl).isBetween(Duration.ofMinutes(10).minusSeconds(5).toMillis(), Duration.ofMinutes(11).toMillis());
        assertThat(quoteTtl).isBetween(1L, 1_100L);   // 1s base (test override) + at most 10%
    }

    @Test
    void entryExpires_andTheNextCallReloadsFromTheRepository() {
        service.quote(1, "EUR");
        assertThat(repository.calls()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(3))
                .until(() -> !redis.hasKey("catalog:quotes::SimpleKey [1, EUR]"));

        service.quote(1, "EUR");
        assertThat(repository.calls()).isEqualTo(2);
    }
}
