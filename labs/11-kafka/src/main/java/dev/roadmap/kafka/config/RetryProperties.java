package dev.roadmap.kafka.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Blocking-retry settings for the listener's DefaultErrorHandler. */
@Validated
@ConfigurationProperties("lab.kafka.retry")
public record RetryProperties(@NotNull Duration interval, @PositiveOrZero long maxRetries) {
}
