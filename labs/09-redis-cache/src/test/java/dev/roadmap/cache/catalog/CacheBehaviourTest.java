package dev.roadmap.cache.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;

import dev.roadmap.cache.RedisIntegrationTest;

class CacheBehaviourTest extends RedisIntegrationTest {

    @Autowired
    ProductService service;

    @Test
    void cacheHit_avoidsRepositoryCall() {
        Product first = service.findById(1);
        Product second = service.findById(1);

        assertThat(second).isEqualTo(first);
        assertThat(repository.calls()).isEqualTo(1);
        assertThat(redis.hasKey("catalog:products::1")).isTrue();   // prefix + cache name + "::" + key
    }

    @Test
    void updatePrice_evictsEntry_soNextReadReloadsFreshValue() {
        service.findById(1);

        service.updatePrice(1, 9_999);   // 2 repo calls: read + save

        assertThat(redis.hasKey("catalog:products::1")).isFalse();
        assertThat(service.findById(1).priceCents()).isEqualTo(9_999);
        assertThat(repository.calls()).isEqualTo(4);
    }

    @Test
    void rename_cachePutStoresResult_andEvictsSkuIndex() {
        service.findBySku("BOOK-001");
        assertThat(redis.hasKey("catalog:skus::BOOK-001")).isTrue();

        service.rename(1, "Effective Java, 3rd ed.");
        int callsAfterRename = repository.calls();

        assertThat(service.findById(1).name()).isEqualTo("Effective Java, 3rd ed.");
        assertThat(repository.calls()).isEqualTo(callsAfterRename);   // @CachePut already wrote it: no reload
        assertThat(redis.hasKey("catalog:skus::BOOK-001")).isFalse();
    }

    @Test
    void selfInvocation_bypassesTheCachingProxy() {
        assertThat(AopUtils.isCglibProxy(service)).isTrue();

        service.priceLabel(1);
        service.priceLabel(1);

        assertThat(repository.calls()).isEqualTo(2);
        assertThat(redis.hasKey("catalog:products::1")).isFalse();
    }

    @Test
    void keySpel_normalizesTheKey() {
        service.findBySku("book-001");
        service.findBySku("BOOK-001");

        assertThat(repository.calls()).isEqualTo(1);
    }

    @Test
    void unlessResultNull_doesNotCacheMisses() {
        assertThat(service.findBySku("NOPE")).isNull();
        assertThat(service.findBySku("NOPE")).isNull();

        assertThat(repository.calls()).isEqualTo(2);
        assertThat(redis.hasKey("catalog:skus::NOPE")).isFalse();
    }

    @Test
    void nullCaching_storesTheMiss_soRepeatedMissesStayOffTheDatabase() {
        assertThat(service.findBySkuCachingMisses("NOPE")).isNull();
        assertThat(service.findBySkuCachingMisses("NOPE")).isNull();

        assertThat(repository.calls()).isEqualTo(1);
        assertThat(redis.hasKey("catalog:skus-negative::NOPE")).isTrue();
    }

    @Test
    void defaultKeyIsSimpleKey_andConditionSkipsTheCache() {
        service.quote(1, "EUR");
        service.quote(1, "EUR");
        assertThat(repository.calls()).isEqualTo(1);
        assertThat(redis.keys("catalog:quotes::*")).containsExactly("catalog:quotes::SimpleKey [1, EUR]");

        service.quote(1, "GBP");
        service.quote(1, "GBP");
        assertThat(repository.calls()).isEqualTo(3);   // condition false: never cached
    }
}
