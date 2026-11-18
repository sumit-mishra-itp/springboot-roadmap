package dev.roadmap.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.roadmap.greeting.GreetingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * application.yml (config data) < Java system property < command-line argument.
 * Uses plain SpringApplication runs: @SpringBootTest caches contexts and its cache key
 * ignores system properties, which would make a system-property test order-dependent.
 */
class PropertyPrecedenceTest {

    private static final String KEY = "greeting.template";

    @AfterEach
    void clearSystemProperty() {
        System.clearProperty(KEY);
    }

    @Test
    void applicationYml_isUsed_whenNothingOverridesIt() {
        assertThat(greetWith()).isEqualTo("Hello, Ada!");
    }

    @Test
    void systemProperty_beatsApplicationYml() {
        System.setProperty(KEY, "System property %s");
        assertThat(greetWith()).isEqualTo("System property Ada");
    }

    @Test
    void commandLineArgument_beatsSystemProperty() {
        System.setProperty(KEY, "System property %s");
        assertThat(greetWith("--greeting.template=Command line %s")).isEqualTo("Command line Ada");
    }

    @Test
    void commandLineArgument_beatsProfileSpecificFile() {
        assertThat(greetWith("--spring.profiles.active=dev", "--greeting.template=Command line %s"))
                .isEqualTo("Command line Ada");
    }

    private static String greetWith(String... args) {
        try (var context = new SpringApplicationBuilder(DemoApplication.class).run(args)) {
            return context.getBean(GreetingService.class).greet("Ada");
        }
    }
}
