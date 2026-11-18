package dev.roadmap.cache.config;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.cache.RedisCacheWriter.TtlFunction;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Our own RedisCacheManager. Defining it makes Boot's cache auto-configuration back off,
 * so spring.cache.redis.* properties are ignored from here on.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfig {

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, CatalogCacheProperties properties) {
        RedisCacheWriter writer = RedisCacheWriter.create(connectionFactory, writerConfig -> writerConfig
                // Spring Data Redis 4.x: on a reactive-capable factory (Lettuce) put/evict are async by default
                // and may land after the method returns. Make them complete before the call returns.
                .immediateWrites()
                // Without a locking writer, sync = true does NOT collapse concurrent misses on Redis (StampedeTest).
                // The lock is one Redis key per CACHE (products~lock), not per entry, and it has a TTL so a
                // crashed holder can't block the cache forever.
                .enableLocking(lock -> lock.lockTimeout(TtlFunction.just(Duration.ofSeconds(5))))
                // SCAN instead of KEYS when a cache is cleared (allEntries = true), so Redis is never blocked.
                .batchStrategy(BatchStrategies.scan(1000)));

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("catalog:")
                .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer()));

        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(writer)
                .cacheDefaults(defaults)
                .disableCreateOnMissingCache()   // a typo in a cache name fails instead of creating a TTL-less cache
                .enableStatistics();
        properties.ttl().forEach((cacheName, ttl) ->
                builder.withCacheConfiguration(cacheName, defaults.entryTtl(jittered(ttl, properties.jitter()))));
        return builder.build();
    }

    /**
     * Jackson 3 serializer. Unlike the deprecated GenericJackson2JsonRedisSerializer, default typing is OFF,
     * so without enableDefaultTyping a cached record reads back as a LinkedHashMap. The validator only lets
     * our own types be named in the "@class" hint (a deserialization-gadget defence).
     */
    static GenericJacksonJsonRedisSerializer jsonSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("dev.roadmap.cache.")
                        .build())
                .enableSpringCacheNullValueSupport()   // lets cached nulls (NullValue) round-trip
                .build();
    }

    /** Base TTL plus 0..jitter*base of random extra, computed per entry when it is written. */
    static TtlFunction jittered(Duration base, double jitter) {
        long maxExtraMillis = (long) (base.toMillis() * jitter);
        return (key, value) -> base.plusMillis(ThreadLocalRandom.current().nextLong(maxExtraMillis + 1));
    }
}
