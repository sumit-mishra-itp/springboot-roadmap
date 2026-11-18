package dev.roadmap.coreioc.proxying;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Full mode (proxyBeanMethods = true, the default). The class is CGLIB-subclassed, so the
 * inter-bean call clock() is intercepted and returns the container's singleton.
 */
@Configuration
public class FullModeConfig {

    @Bean
    Clock fullClock() {
        return new Clock();
    }

    @Bean
    ClockHolder fullHolder() {
        return new ClockHolder(fullClock()); // same instance as the fullClock bean
    }
}
