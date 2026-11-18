package dev.roadmap.cache.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import dev.roadmap.cache.RedisIntegrationTest;

class SerializationTest extends RedisIntegrationTest {

    @Autowired
    ProductService service;

    @Autowired
    CacheManager cacheManager;

    @Test
    void recordIsStoredAsTypedJson_andReadsBackAsTheRecord() {
        Product original = service.findById(1);

        String raw = redis.opsForValue().get("catalog:products::1");
        assertThat(raw)
                .startsWith("{")
                .contains("\"@class\":\"dev.roadmap.cache.catalog.Product\"")
                .contains("\"name\":\"Effective Java\"");

        // Read straight from Redis through the Cache API: this deserializes the bytes, no in-memory copy involved.
        Cache.ValueWrapper fromRedis = cacheManager.getCache("products").get(1L);
        assertThat(fromRedis.get()).isInstanceOf(Product.class).isEqualTo(original);
    }

    @Test
    void unknownCacheName_isRejected_notSilentlyCreated() {
        assertThat(cacheManager.getCache("prodcuts")).isNull();
    }
}
