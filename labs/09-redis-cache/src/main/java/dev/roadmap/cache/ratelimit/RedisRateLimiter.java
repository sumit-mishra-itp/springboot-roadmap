package dev.roadmap.cache.ratelimit;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Fixed-window rate limiter on plain Redis ops: INCR a per-client counter and give it a TTL on the first hit.
 * INCR and PEXPIRE run in one Lua script. Sent as two commands, a crash between them would leave a counter
 * with no TTL, and that client would stay blocked forever.
 * ponytail: fixed window allows up to 2x limit across a window boundary. Use a sliding-window log (ZSET) or
 * a token bucket if that burst matters.
 */
@Component
public class RedisRateLimiter {

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> INCREMENT_WITH_TTL = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return {count, redis.call('PTTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;

    public RedisRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public Decision tryAcquire(String clientId) {
        List<?> result = redis.execute(INCREMENT_WITH_TTL, List.of("rate:" + clientId),
                String.valueOf(properties.window().toMillis()));
        long count = (Long) result.get(0);
        long ttlMillis = (Long) result.get(1);
        return new Decision(count <= properties.limit(), Duration.ofMillis(Math.max(ttlMillis, 0)));
    }

    public record Decision(boolean allowed, Duration retryAfter) {
    }
}
