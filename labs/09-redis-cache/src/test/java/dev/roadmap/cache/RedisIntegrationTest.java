package dev.roadmap.cache;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import dev.roadmap.cache.catalog.ProductRepository;

/**
 * Every test class extends this, so they all share ONE Spring context and ONE Redis container
 * (identical configuration means the TestContext cache reuses the context).
 * Each test starts from an empty Redis and a zeroed repository call counter.
 */
@SpringBootTest(properties = {
        "app.cache.ttl.quotes=1s",
        "app.rate-limit.limit=3",
        "app.rate-limit.window=2s"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class RedisIntegrationTest {

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    protected ProductRepository repository;

    @BeforeEach
    void startFromEmptyRedis() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        repository.reset();
    }
}
