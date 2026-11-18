# Lab 07: Spring Security 7, stateless JWT API

Companion to [note 07](../../notes/07-security/spring-security.md). Spring Boot 4.1.2, which ships **Spring Security 7.1.2**.

## What it demonstrates

| Feature | Where |
|---|---|
| Self-issued RS256 access tokens with Spring's own `NimbusJwtEncoder` (7.0 builder) and `NimbusJwtDecoder`. No hand-written JWT filter | `config/JwtConfig` |
| `exp`/`nbf` + `typ` + **issuer + audience** validation | `JwtConfig#jwtDecoder` |
| `roles` claim → `ROLE_*` via `JwtAuthenticationConverter` | `JwtConfig#jwtAuthenticationConverter` |
| `/auth/login` issues an access token plus an opaque refresh token | `auth/AuthController`, `AuthService` |
| Refresh-token **rotation with reuse detection** (a token family is revoked on replay) | `auth/RefreshTokenStore` |
| `DelegatingPasswordEncoder` (`{bcrypt}`), plus transparent upgrade of a legacy `{noop}` hash on login | `auth/UserConfig` |
| Three `SecurityFilterChain`s: actuator (Basic), `/api/**` + `/auth/**` (JWT), and a deny-by-default catch-all | `config/ActuatorSecurityConfig`, `config/SecurityConfig` |
| Lambda DSL URL rules, CORS, CSRF disabled only on the bearer-token chain | `config/SecurityConfig` |
| `@EnableMethodSecurity`, `@PreAuthorize` with a bean reference (`@documentGuard`), and `@PostAuthorize` on `returnObject` | `document/DocumentService` |
| 401/403 as RFC 9457 `ProblemDetail` through `AuthenticationEntryPoint` / `AccessDeniedHandler` | `config/ProblemDetailSecurityHandler` |

**Why there's no database:** the task allowed dropping JPA. Users live in an `InMemoryUserDetailsManager`, and documents and refresh tokens live in `ConcurrentHashMap`s, so every test is about security, not persistence, and the build needs no Docker. Swapping in `JdbcUserDetailsManager` and a refresh-token table changes no security code. The `ponytail:` comments mark the production upgrade paths: a key pair from KMS/Secrets Manager, and an atomic DB/Redis rotation.

## Run it

```bash
./mvnw verify            # 38 tests, all green
./mvnw spring-boot:run   # then try:
curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"username":"alice","password":"alice-pass"}'
curl -s localhost:8080/api/me -H "Authorization: Bearer <accessToken>"
curl -s -X POST localhost:8080/auth/refresh -H 'Content-Type: application/json' -d '{"refreshToken":"<refreshToken>"}'
curl -s -u admin:admin-pass localhost:8080/actuator/info
```

Demo users: `alice`/`alice-pass` (USER), `bob`/`bob-pass` (USER), `admin`/`admin-pass` (USER, ADMIN), and `legacy`/`legacy-pass` (USER, stored as `{noop}`).

## What each test proves

| Test | Proves |
|---|---|
| `FilterChainOrderTest` | `DelegatingFilterProxy` (registered as `springSecurityFilterChain`) → `FilterChainProxy` → chains tried in `@Order`, with the catch-all last. It asserts the **exact filter order** of the JWT chain, the actuator Basic chain, and a test-only form-login chain |
| `AuthFlowEndToEndTest` (`@SpringBootTest` + `RestTestClient`) | Login (no `Set-Cookie`), a malformed bearer token rejected even on a `permitAll` URL, roles conversion (`FACTOR_BEARER`, `ROLE_USER`), `FACTOR_PASSWORD` on password login, the 60s clock skew, 401 without a token or with a garbage token, 403 on the wrong role, `@PostAuthorize` denial, **rotation plus reuse detection that revokes the whole family**, a refresh token rejected as an access token, expired and wrong-audience JWTs rejected, legacy hash upgraded to bcrypt, actuator chain is Basic-only, and unmatched paths are denied |
| `DocumentControllerSecurityTest` (`@WebMvcTest`) | `jwt()` and `@WithMockUser` post-processors, URL rules, `@PreAuthorize` domain rule, `@PostAuthorize`, and CORS preflight allowed or rejected before authentication |
| `PasswordStorageTest` | The `{bcrypt}$2a$10$` format, salting, and `upgradeEncoding` for legacy hashes |
| `SecurityContextPropagationTest` | The ThreadLocal strategy. A pool thread sees no `Authentication`, and `DelegatingSecurityContextExecutorService` copies it across |
