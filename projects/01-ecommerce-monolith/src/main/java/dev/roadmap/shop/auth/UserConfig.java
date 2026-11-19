package dev.roadmap.shop.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The JPA-backed half of auth: where users come from and how /api/auth/login checks a password.
 * Kept apart from SecurityConfig so web-slice tests can load the filter chain without any repository.
 */
@Configuration(proxyBeanMethods = false)
public class UserConfig {

    private static final Logger log = LoggerFactory.getLogger(UserConfig.class);

    @Bean
    UserDetailsService userDetailsService(UserAccountRepository users) {
        return username -> users.findByUsername(username)
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user")); // becomes "Bad credentials"
    }

    /**
     * Boot exposes no AuthenticationManager bean, so the login endpoint builds one:
     * ProviderManager -> DaoAuthenticationProvider -> UserDetailsService + PasswordEncoder. For an unknown username
     * DaoAuthenticationProvider still runs one bcrypt check, so response time does not reveal which usernames exist.
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    /**
     * Customers self-register, admins cannot. The first admin comes from the environment (APP_AUTH_ADMIN_PASSWORD),
     * never from a password or hash committed in a Flyway script.
     */
    // ponytail: two instances starting at once can both try the INSERT; the loser fails on the unique username.
    // Run the bootstrap as a one-off job (or a Flyway Java migration) if the app is ever scaled out.
    @Bean
    ApplicationRunner adminBootstrap(AuthProperties props, UserAccountRepository users, PasswordEncoder encoder) {
        return args -> {
            String password = props.adminPassword();
            if (password == null || password.isBlank()) {
                log.info("APP_AUTH_ADMIN_PASSWORD not set, no admin account bootstrapped");
                return;
            }
            if (users.existsByUsername(props.adminUsername())) {
                return;
            }
            users.save(new UserAccount(props.adminUsername(), encoder.encode(password), Role.ADMIN));
            log.info("Bootstrapped admin account '{}'", props.adminUsername());
        };
    }
}
