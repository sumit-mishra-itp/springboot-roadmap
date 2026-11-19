package dev.roadmap.shop.auth;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated auth settings: a typo fails startup instead of minting broken tokens.
 * adminPassword has no default on purpose. It comes from the APP_AUTH_ADMIN_PASSWORD env var, and without it no
 * admin account is bootstrapped.
 */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotBlank String adminUsername,
        @Nullable String adminPassword) {

    /** A record's generated toString prints every component, and this one holds a password. */
    @Override
    public String toString() {
        return "AuthProperties[issuer=" + issuer + ", audience=" + audience + ", accessTokenTtl=" + accessTokenTtl
                + ", refreshTokenTtl=" + refreshTokenTtl + ", adminUsername=" + adminUsername + ", adminPassword=***]";
    }
}
