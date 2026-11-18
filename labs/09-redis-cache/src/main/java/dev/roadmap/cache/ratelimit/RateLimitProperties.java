package dev.roadmap.cache.ratelimit;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** At most `limit` requests per client per `window`. */
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(@Positive int limit, @NotNull Duration window) {
}
