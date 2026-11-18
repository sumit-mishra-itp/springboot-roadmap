package dev.roadmap.security.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.time.Duration;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The JWT API chain plus a deny-by-default fallback. The actuator chain lives in ActuatorSecurityConfig.
 * FilterChainProxy tries chains in @Order order and uses the FIRST whose securityMatcher matches the request.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity // turns on @PreAuthorize / @PostAuthorize (prePostEnabled is true by default)
public class SecurityConfig {

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
            ProblemDetailSecurityHandler problems) {
        return http
                // /auth/** is listed on purpose: a request that matches NO chain gets NO security filters at all.
                .securityMatcher("/api/**", "/auth/**")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/refresh").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                // Spring's resource-server support, not a hand-rolled filter: BearerTokenAuthenticationFilter
                // -> JwtAuthenticationProvider -> JwtDecoder (signature + exp/iss/aud) -> our converter.
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problems)   // invalid/expired token (fails inside the bearer filter)
                        .accessDeniedHandler(problems))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)   // no token at all (anonymous hits AuthorizationFilter)
                        .accessDeniedHandler(problems))       // authenticated, wrong role
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF abuses AMBIENT credentials (cookies, Basic) that the browser attaches by itself. A bearer
                // token is attached by our JavaScript only, so a forged cross-site request carries no credentials.
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults()) // picks up the CorsConfigurationSource bean below
                .build();
    }

    /** No securityMatcher means "any request", so this chain must be LAST or Spring refuses to start. */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityFilterChain denyByDefaultChain(HttpSecurity http, ProblemDetailSecurityHandler problems) {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/error").permitAll() // ERROR dispatches go through security too
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /**
     * Only the SPA origin may call us from a browser. The preflight (OPTIONS) is answered by CorsFilter, before auth.
     * The CONCRETE return type matters: every HttpSecurity auto-applies cors() when a bean of type
     * UrlBasedCorsConfigurationSource is visible. With the interface as return type, only chains built AFTER this
     * bean happened to be instantiated got a CorsFilter (FilterChainOrderTest caught that).
     */
    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(AppSecurityProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setMaxAge(Duration.ofHours(1)); // browsers cache the preflight answer
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
