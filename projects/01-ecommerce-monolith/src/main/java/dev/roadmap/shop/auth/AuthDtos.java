package dev.roadmap.shop.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** API-boundary records for the auth feature. Every record holding a secret masks it in toString. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * bcrypt only reads the first 72 bytes of a password, and Spring Security 7's BCryptPasswordEncoder rejects
     * longer input (AuthApiTest proves it). The cap turns that into a 400 instead of a 500.
     * ponytail: @Size counts chars, not bytes, so a non-ASCII password of 72 chars but over 72 UTF-8 bytes still reaches
     * the encoder and becomes a 500. Add a custom byte-length constraint if that matters.
     */
    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "may contain letters, digits, '.', '_' or '-'") String username,
            @NotBlank @Size(min = 8, max = 72) String password) {
        @Override
        public String toString() {
            return "RegisterRequest[username=" + username + ", password=***]";
        }
    }

    /** No length rules on login: they would tell an attacker about the password policy and add nothing. */
    public record LoginRequest(@NotBlank String username, @NotBlank @Size(max = 72) String password) {
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
        @Override
        public String toString() {
            return "TokenResponse[accessToken=***, refreshToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
        }
    }

    public record UserResponse(long id, String username, Role role) {
        static UserResponse from(UserAccount account) {
            return new UserResponse(account.getId(), account.getUsername(), account.getRole());
        }
    }
}
