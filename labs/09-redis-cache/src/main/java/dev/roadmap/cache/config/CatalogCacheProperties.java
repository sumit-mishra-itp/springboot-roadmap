package dev.roadmap.cache.config;

import java.time.Duration;
import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * app.cache.ttl.(cache-name) = base TTL per cache.
 * app.cache.jitter = up to this fraction of the base TTL is added at random, so entries written together
 * don't all expire together (cache avalanche).
 */
@Validated
@ConfigurationProperties("app.cache")
public record CatalogCacheProperties(
        @NotEmpty Map<String, Duration> ttl,
        @DecimalMin("0.0") @DecimalMax("0.5") double jitter) {
}
