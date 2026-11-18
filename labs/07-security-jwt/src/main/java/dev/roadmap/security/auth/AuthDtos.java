package dev.roadmap.security.auth;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

/** API-boundary records for the auth feature. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** toString is overridden so the password can never end up in a log line. */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
        @Override
        public String toString() {
            return "LoginRequest[username=" + username + ", password=***]";
        }
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
        @Override
        public String toString() {
            return "RefreshRequest[refreshToken=***]";
        }
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
    }

    public record Me(String name, List<String> authorities) {
    }
}
