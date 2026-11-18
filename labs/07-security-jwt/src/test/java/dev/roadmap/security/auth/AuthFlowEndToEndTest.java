package dev.roadmap.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.web.servlet.client.RestTestClient;

import dev.roadmap.security.auth.AuthDtos.LoginRequest;
import dev.roadmap.security.auth.AuthDtos.Me;
import dev.roadmap.security.auth.AuthDtos.RefreshRequest;
import dev.roadmap.security.auth.AuthDtos.TokenResponse;

/** Real server, real tokens, real filter chains: the whole story from login to reuse detection. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class AuthFlowEndToEndTest {

    @Autowired
    RestTestClient client;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    InMemoryUserDetailsManager users;

    @Autowired
    AuthenticationManager authenticationManager;

    @Test
    void login_issuesTokens_withoutCreatingASession() {
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("alice", "alice-pass"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE) // STATELESS: no JSESSIONID
                .expectBody()
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.expiresIn").isEqualTo(300);
    }

    @Test
    void bearerToken_isConvertedToRolesByJwtAuthenticationConverter() {
        String token = login("alice", "alice-pass").accessToken();

        Me me = client.get().uri("/api/me").headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Me.class).returnResult().getResponseBody();

        assertThat(me.name()).isEqualTo("alice");
        // "roles":["USER"] became ROLE_USER. Security 7 also records HOW we authenticated: FACTOR_BEARER.
        assertThat(me.authorities()).containsExactly("FACTOR_BEARER", "ROLE_USER");
    }

    @Test
    void protectedEndpoint_withValidToken_returnsOwnDocument() {
        String token = login("alice", "alice-pass").accessToken();

        client.get().uri("/api/documents/1").headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.owner").isEqualTo("alice");
    }

    @Test
    void protectedEndpoint_withoutToken_returns401ProblemDetail() {
        client.get().uri("/api/documents/1")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.detail").isEqualTo("Full authentication is required to access this resource")
                .jsonPath("$.instance").isEqualTo("/api/documents/1");
    }

    @Test
    void permitAllEndpoint_withMalformedBearerToken_isStillRejected() {
        // permitAll() is decided by AuthorizationFilter, but BearerTokenAuthenticationFilter runs (and fails) first.
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth("stale-or-garbage"))
                .body(new LoginRequest("alice", "alice-pass"))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401ProblemDetail() {
        // Fails inside BearerTokenAuthenticationFilter, so it uses the resource server's entry point.
        client.get().uri("/api/documents/1").headers(h -> h.setBearerAuth("not-a-jwt"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void login_withWrongPassword_returns401() {
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("alice", "wrong"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.detail").isEqualTo("Bad credentials");
    }

    @Test
    void login_withBlankFields_returns400ProblemDetail() {
        client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("", ""))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void adminUrl_withUserRole_returns403ProblemDetail() {
        String token = login("alice", "alice-pass").accessToken();

        client.delete().uri("/api/admin/documents/2").headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(403);
    }

    @Test
    void otherUsersDocument_isRejectedByPostAuthorize() {
        String token = login("bob", "bob-pass").accessToken();

        client.get().uri("/api/documents/1").headers(h -> h.setBearerAuth(token))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void refresh_rotatesTokens_andReuseRevokesTheWholeFamily() {
        TokenResponse first = login("alice", "alice-pass");

        TokenResponse second = refresh(first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        client.get().uri("/api/me").headers(h -> h.setBearerAuth(second.accessToken()))
                .exchange().expectStatus().isOk();

        // An attacker (or the victim) replays the already-used token: reuse detected...
        client.post().uri("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .body(new RefreshRequest(first.refreshToken()))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.detail")
                .isEqualTo("Refresh token reuse detected, all tokens of this login revoked");

        // ...and the newest token of the same family is dead too, so whoever stole it is locked out.
        client.post().uri("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .body(new RefreshRequest(second.refreshToken()))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void refreshToken_isNotAcceptedAsAccessToken() {
        String refreshToken = login("alice", "alice-pass").refreshToken();

        client.get().uri("/api/me").headers(h -> h.setBearerAuth(refreshToken))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void expiredToken_and_wrongAudience_areRejected() {
        Instant past = Instant.now().minusSeconds(600);
        String expired = mint(JwtClaimsSet.builder().issuer("http://localhost:8080").audience(List.of("documents-api"))
                .subject("alice").issuedAt(past).expiresAt(past.plusSeconds(120)).claim("roles", List.of("USER")));
        String otherApi = mint(JwtClaimsSet.builder().issuer("http://localhost:8080").audience(List.of("billing-api"))
                .subject("alice").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(120))
                .claim("roles", List.of("USER")));

        client.get().uri("/api/me").headers(h -> h.setBearerAuth(expired))
                .exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/me").headers(h -> h.setBearerAuth(otherApi))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void tokenExpired30SecondsAgo_isStillAccepted_dueToDefaultClockSkew() {
        Instant now = Instant.now();
        String justExpired = mint(JwtClaimsSet.builder().issuer("http://localhost:8080")
                .audience(List.of("documents-api")).subject("alice")
                .issuedAt(now.minusSeconds(300)).expiresAt(now.minusSeconds(30)).claim("roles", List.of("USER")));

        client.get().uri("/api/me").headers(h -> h.setBearerAuth(justExpired))
                .exchange().expectStatus().isOk(); // JwtTimestampValidator allows 60s of skew
    }

    @Test
    void passwordLogin_addsFactorPasswordAuthority_whichIsNotCopiedIntoTheToken() {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("alice", "alice-pass"));

        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("FACTOR_PASSWORD", "ROLE_USER");
    }

    @Test
    void legacyPasswordHash_isUpgradedToBcrypt_onSuccessfulLogin() {
        assertThat(users.loadUserByUsername("legacy").getPassword()).startsWith("{noop}");

        login("legacy", "legacy-pass");

        assertThat(users.loadUserByUsername("legacy").getPassword()).startsWith("{bcrypt}");
    }

    @Test
    void actuatorChain_usesBasicAuth_notBearerTokens() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();

        client.get().uri("/actuator/info").exchange()
                .expectStatus().isUnauthorized()
                // Security 7.1 (gh-18755) added charset to the Basic challenge.
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"Realm\", charset=\"UTF-8\"");

        String adminToken = login("admin", "admin-pass").accessToken();
        client.get().uri("/actuator/info").headers(h -> h.setBearerAuth(adminToken))
                .exchange().expectStatus().isUnauthorized(); // this chain has no BearerTokenAuthenticationFilter

        client.get().uri("/actuator/info").headers(h -> h.setBasicAuth("alice", "alice-pass"))
                .exchange().expectStatus().isForbidden();
        client.get().uri("/actuator/info").headers(h -> h.setBasicAuth("admin", "admin-pass"))
                .exchange().expectStatus().isOk();
    }

    @Test
    void unmatchedPath_fallsThroughToDenyByDefaultChain() {
        client.get().uri("/internal/anything").exchange().expectStatus().isUnauthorized();
    }

    private TokenResponse login(String username, String password) {
        return client.post().uri("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(username, password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody();
    }

    private TokenResponse refresh(String refreshToken) {
        return client.post().uri("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .body(new RefreshRequest(refreshToken))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody();
    }

    private String mint(JwtClaimsSet.Builder claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }
}
