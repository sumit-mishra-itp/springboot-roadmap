package dev.roadmap.security.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Users and the AuthenticationManager used by /auth/login.
 * In-memory on purpose: this lab is about the security pipeline, not persistence. Swap in
 * JdbcUserDetailsManager or a JPA-backed UserDetailsService and nothing else changes.
 */
@Configuration(proxyBeanMethods = false)
public class UserConfig {

    /** Writes {bcrypt}..., and still reads {noop}, {pbkdf2}, {argon2}... so old hashes keep working. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Demo users for a throwaway lab. Real credentials never live in source code. */
    @Bean
    InMemoryUserDetailsManager userDetailsService(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("alice").password(encoder.encode("alice-pass")).roles("USER").build(),
                User.withUsername("bob").password(encoder.encode("bob-pass")).roles("USER").build(),
                User.withUsername("admin").password(encoder.encode("admin-pass")).roles("USER", "ADMIN").build(),
                // A user imported from a legacy system with a weak hash. It is upgraded to bcrypt on first login.
                User.withUsername("legacy").password("{noop}legacy-pass").roles("USER").build());
    }

    /**
     * Boot does not expose an AuthenticationManager bean, so a login endpoint builds one:
     * ProviderManager -> DaoAuthenticationProvider -> UserDetailsService + PasswordEncoder.
     */
    @Bean
    AuthenticationManager authenticationManager(InMemoryUserDetailsManager users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        provider.setUserDetailsPasswordService(users); // re-hashes outdated encodings after a successful login
        return new ProviderManager(provider);
    }
}
