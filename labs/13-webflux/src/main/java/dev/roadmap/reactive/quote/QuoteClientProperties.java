package dev.roadmap.reactive.quote;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("quotes")
@Validated
public record QuoteClientProperties(@NotNull URI baseUrl, @NotNull Duration timeout,
                                    @PositiveOrZero int maxRetries, @NotNull Duration firstBackoff) {
}
