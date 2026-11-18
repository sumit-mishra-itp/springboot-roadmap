package dev.roadmap.cache.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.roadmap.cache.RedisIntegrationTest;

/** Test config: 3 requests per client per 2s window. */
class RateLimitTest extends RedisIntegrationTest {

    @Autowired
    RedisRateLimiter limiter;

    @Autowired
    MockMvcTester mvc;

    @Test
    void allowsLimitPerWindow_thenRejects_thenResetsWhenTheKeyExpires() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("alice").allowed()).isTrue();
        }
        RedisRateLimiter.Decision rejected = limiter.tryAcquire("alice");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(2));
        assertThat(limiter.tryAcquire("bob").allowed()).isTrue();   // counters are per client

        await().atMost(Duration.ofSeconds(4)).until(() -> !redis.hasKey("rate:alice"));
        assertThat(limiter.tryAcquire("alice").allowed()).isTrue();
    }

    @Test
    void fourthRequest_gets429ProblemDetail_withRetryAfter() {
        for (int i = 0; i < 3; i++) {
            assertThat(mvc.get().uri("/products/1").header("X-Client-Id", "carol")).hasStatusOk();
        }

        assertThat(mvc.get().uri("/products/1").header("X-Client-Id", "carol"))
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .containsHeader("Retry-After");
    }

    @Test
    void unknownProduct_is404ProblemDetail() {
        assertThat(mvc.get().uri("/products/999").header("X-Client-Id", "dave"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
