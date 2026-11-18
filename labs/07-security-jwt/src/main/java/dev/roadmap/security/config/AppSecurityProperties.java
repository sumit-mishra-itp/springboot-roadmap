package dev.roadmap.security.config;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed, validated settings: a typo in application.properties fails startup instead of minting broken tokens. */
@Validated
@ConfigurationProperties("app.security")
public record AppSecurityProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotEmpty List<String> allowedOrigins) {
}
