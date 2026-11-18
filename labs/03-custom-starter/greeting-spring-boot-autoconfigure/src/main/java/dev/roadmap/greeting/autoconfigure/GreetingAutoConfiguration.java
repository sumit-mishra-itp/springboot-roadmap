package dev.roadmap.greeting.autoconfigure;

import dev.roadmap.greeting.GreetingService;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Discovered ONLY through META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 * It sits in a package no application scans, so it is always processed after user configuration,
 * which is what lets {@link ConditionalOnMissingBean} see (and yield to) a user-defined bean.
 */
@AutoConfiguration
@ConditionalOnClass(GreetingService.class)               // the library is on the classpath
@ConditionalOnProperty(prefix = "greeting", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(GreetingProperties.class) // registers + binds + validates the record
public final class GreetingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean // matches on the return type: any user bean assignable to GreetingService wins
    GreetingService greetingService(GreetingProperties properties) {
        return new GreetingService(properties.template());
    }
}
