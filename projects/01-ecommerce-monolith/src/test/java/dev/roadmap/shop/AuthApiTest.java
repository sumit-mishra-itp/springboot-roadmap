package dev.roadmap.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import dev.roadmap.shop.auth.AuthDtos.LoginRequest;
import dev.roadmap.shop.auth.AuthDtos.RefreshRequest;
import dev.roadmap.shop.auth.AuthDtos.RegisterRequest;
import dev.roadmap.shop.auth.AuthDtos.TokenResponse;
import dev.roadmap.shop.common.ApiExceptionHandler;

/** Register, login, token contents, refresh rotation with reuse detection, and the filter chain's 401 cases. */
class AuthApiTest extends ApiTestSupport {

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    JdbcClient jdbc;

    @Test
    void register_createsACustomer_storesOnlyABcryptHash_andNeverEchoesThePassword() {
        client.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("new-user-1", "s3cret-pass"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.username").isEqualTo("new-user-1")
                .jsonPath("$.role").isEqualTo("CUSTOMER")
                .jsonPath("$.password").doesNotExist()
                .jsonPath("$.passwordHash").doesNotExist();

        String hash = jdbc.sql("select password_hash from users where username = 'new-user-1'").query(String.class).single();
        assertThat(hash).startsWith("{bcrypt}$2a$10$").doesNotContain("s3cret-pass");
    }

    @Test
    void register_takenUsername_returns409Problem() {
        Customer existing = newCustomer();

        client.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(existing.username(), "another-pass"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "conflict");
    }

    @Test
    void register_invalidInput_returns400WithFieldErrors() {
        client.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("a b", "x".repeat(73))) // bcrypt can't take more than 72 bytes
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "validation-error")
                .jsonPath("$.errors[0].field").isEqualTo("password")
                .jsonPath("$.errors[1].field").isEqualTo("username");
    }

    @Test
    void bcrypt_rejectsPasswordsOver72Bytes_whichIsWhyRegisterCapsTheLength() {
        assertThatThrownBy(() -> new BCryptPasswordEncoder().encode("x".repeat(73)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void login_wrongPassword_and_unknownUser_bothReturnTheSame401Problem() {
        Customer customer = newCustomer();

        for (String username : List.of(customer.username(), "nobody-" + customer.username())) {
            client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .body(new LoginRequest(username, "wrong-pass"))
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .expectBody()
                    .jsonPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "unauthorized")
                    .jsonPath("$.detail").isEqualTo("Bad credentials"); // no hint which part was wrong
        }
    }

    @Test
    void accessToken_carriesTheUserIdAsSubject_andTheRoleClaim() {
        Customer customer = newCustomer();

        Jwt jwt = jwtDecoder.decode(customer.accessToken());

        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(customer.id()));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(jwt.getClaimAsString("preferred_username")).isEqualTo(customer.username());
        assertThat(jwt.getAudience()).containsExactly("shopsphere-api");
        assertThat(jwt.getExpiresAt()).isBefore(Instant.now().plusSeconds(15 * 60 + 5));
    }

    @Test
    void refresh_rotatesTokens_andReuseRevokesTheWholeFamily_evenThoughTheRequestFails() {
        Customer customer = newCustomer();
        TokenResponse first = login(customer.username(), CUSTOMER_PASSWORD);

        TokenResponse second = refresh(first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        client.get().uri("/api/orders").headers(bearer(second.accessToken())).exchange().expectStatus().isOk();

        // The used token comes back (an attacker's copy, or the victim after the attacker rotated): reuse detected.
        client.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .body(new RefreshRequest(first.refreshToken()))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.detail").isEqualTo("Refresh token reuse detected, all tokens of this login revoked");

        // The newest token of that family is dead too. This only holds because AuthService.refresh is
        // @Transactional(noRollbackFor = InvalidRefreshTokenException.class): a rollback would undo the revocation.
        client.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .body(new RefreshRequest(second.refreshToken()))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void refresh_withUnknownToken_returns401() {
        client.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .body(new RefreshRequest("made-up"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.detail").isEqualTo("Refresh token is invalid");
    }

    @Test
    void refreshToken_isNotAcceptedAsAccessToken() {
        Customer customer = newCustomer();
        String refreshToken = login(customer.username(), CUSTOMER_PASSWORD).refreshToken();

        client.get().uri("/api/orders").headers(bearer(refreshToken)).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void tokenMintedForAnotherAudience_isRejected() {
        Instant now = Instant.now();
        String otherApi = jwtEncoder.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
                .issuer("https://api.shopsphere.dev").audience(List.of("billing-api")).subject("1")
                .issuedAt(now).expiresAt(now.plusSeconds(60)).claim("roles", List.of("CUSTOMER")).build())).getTokenValue();

        client.get().uri("/api/orders").headers(bearer(otherApi)).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void malformedBearerToken_isRejected_evenOnThePublicCatalog() {
        // permitAll is decided by AuthorizationFilter, but BearerTokenAuthenticationFilter runs (and fails) first.
        // So a client with a stale token must drop it, not send it to public endpoints.
        client.get().uri("/api/products").headers(bearer("stale-or-garbage")).exchange().expectStatus().isUnauthorized();
    }

    @Test
    void unmatchedPath_isDeniedByDefault() {
        client.get().uri("/internal/anything").exchange().expectStatus().isUnauthorized();
    }

    private TokenResponse refresh(String refreshToken) {
        return client.post().uri("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .body(new RefreshRequest(refreshToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody();
    }
}
