package dev.roadmap.security.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Ops endpoints get their own chain: HTTP Basic for the monitoring system, health open for load balancers.
 * EndpointRequest matches the real actuator paths, even if management.endpoints.web.base-path changes.
 * Kept in its own class so the @WebMvcTest slice (no actuator auto-config) can import SecurityConfig alone.
 */
@Configuration(proxyBeanMethods = false)
public class ActuatorSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain actuatorChain(HttpSecurity http) {
        return http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(EndpointRequest.to("health")).permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .httpBasic(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF stays ON here: browsers cache Basic credentials and resend them automatically, which is
                // exactly the "ambient credential" CSRF exploits. We only expose GET endpoints, so it costs nothing.
                .build();
    }
}
