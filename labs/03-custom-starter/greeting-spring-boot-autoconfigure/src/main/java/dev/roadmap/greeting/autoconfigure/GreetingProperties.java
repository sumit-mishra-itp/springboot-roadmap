package dev.roadmap.greeting.autoconfigure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated settings under the {@code greeting} prefix. A record means constructor
 * binding: immutable, and a value is null unless {@link DefaultValue} supplies one.
 *
 * @param enabled Whether to auto-configure a GreetingService
 * @param template Message template; must contain a single %s placeholder for the name
 */
@Validated
@ConfigurationProperties("greeting")
public record GreetingProperties(
        @DefaultValue("true") boolean enabled,
        @NotBlank @Pattern(regexp = ".*%s.*", message = "must contain %s") @DefaultValue("Hello, %s!") String template) {
}
