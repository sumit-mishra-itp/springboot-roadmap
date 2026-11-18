package dev.roadmap.demo.greeting;

import dev.roadmap.greeting.GreetingService;
import dev.roadmap.greeting.autoconfigure.GreetingProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Overrides the starter's bean, but only when the "loud" profile is active.
 * User configuration is parsed before auto-configuration, so the starter's
 * {@code @ConditionalOnMissingBean} sees this bean and backs off.
 */
@Configuration(proxyBeanMethods = false)
@Profile("loud")
class LoudGreetingConfig {

    @Bean
    GreetingService greetingService(GreetingProperties properties) { // reuses the starter's validated properties
        return new LoudGreetingService(properties.template());
    }
}
