package dev.roadmap.shop.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The stateless JWT setup proven in lab 07: self-issued RS256 tokens, Spring's own resource-server filter (no
 * hand-written JwtFilter), and 401/403 as ProblemDetail. It depends on no repository, so the @WebMvcTest slices
 * import this one class and test the real URL rules. Users and login live in UserConfig.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
@EnableMethodSecurity // @PreAuthorize on OrderService is the ONLY place the CUSTOMER role is checked
public class SecurityConfig {

    /**
     * One chain, deny by default. The rules are tried top to bottom and the first match wins.
     * Catalog writes are guarded here by URL. Orders only need "authenticated" here, because OrderService's
     * @PreAuthorize decides who may use them (OrderApiTest proves an ADMIN token gets 403 from it).
     */
    @Bean
    SecurityFilterChain apiChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) {
        ProblemDetailSecurityHandler problems = new ProblemDetailSecurityHandler();
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll() // ERROR dispatches go through security too
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**").permitAll()
                        .requestMatchers("/api/products/**", "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers("/api/orders/**").authenticated()
                        .anyRequest().denyAll())
                // BearerTokenAuthenticationFilter -> JwtAuthenticationProvider -> JwtDecoder -> our converter.
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problems)   // invalid or expired token (fails inside the bearer filter)
                        .accessDeniedHandler(problems))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)   // no token at all
                        .accessDeniedHandler(problems))       // authenticated, wrong role (URL rule or @PreAuthorize)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF abuses AMBIENT credentials (cookies, Basic). A bearer token is attached by the client's code
                // only, so a forged cross-site request carries no credentials. There is no session cookie at all.
                .csrf(csrf -> csrf.disable())
                .build();
    }

    // ponytail: the key pair is generated at every start, so access tokens die on restart (clients refresh, since
    // refresh tokens live in Postgres) and several instances can't share tokens. Production loads the private key
    // from Secrets Manager or KMS and publishes the public half as a JWK set.
    @Bean
    KeyPair jwtSigningKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Bean
    JwtEncoder jwtEncoder(KeyPair keyPair) {
        return NimbusJwtEncoder.withKeyPair((RSAPublicKey) keyPair.getPublic(), (RSAPrivateKey) keyPair.getPrivate())
                .build();
    }

    /** Signature, exp/nbf (60s skew) and typ by default, plus issuer and audience so a token for another API fails. */
    @Bean
    JwtDecoder jwtDecoder(KeyPair keyPair, AuthProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(props.issuer()),
                new JwtAudienceValidator(props.audience())));
        return decoder;
    }

    /**
     * "roles":["CUSTOMER"] becomes ROLE_CUSTOMER. Without this Spring reads "scope" and adds SCOPE_, so hasRole()
     * would never match. The principal name stays the "sub" claim, which holds the user id.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    /** Writes {bcrypt}..., and still reads other {id} prefixes, so the algorithm can be upgraded later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
