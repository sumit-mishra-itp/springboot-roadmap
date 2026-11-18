package dev.roadmap.coreioc.proxying;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Lite mode (proxyBeanMethods = false). There is no CGLIB subclass, so calling liteClock() is a plain
 * Java call that creates a SECOND Clock. Correct style: take the dependency as a method parameter.
 */
@Configuration(proxyBeanMethods = false)
public class LiteModeConfig {

    @Bean
    Clock liteClock() {
        return new Clock();
    }

    @Bean
    ClockHolder liteHolderByCall() {
        return new ClockHolder(liteClock()); // NOT the liteClock bean
    }

    @Bean
    ClockHolder liteHolderByParam(Clock liteClock) {
        return new ClockHolder(liteClock); // the liteClock bean (resolved by parameter name)
    }
}
