package dev.roadmap.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.roadmap.demo.greeting.LoudGreetingService;
import dev.roadmap.greeting.GreetingService;
import org.junit.jupiter.api.Test;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts the real app with different --spring.profiles.active values. Plain SpringApplication
 * (not @SpringBootTest) so every case gets a fresh context and nothing is cached between them.
 */
class ProfilesTest {

    @Test
    void noProfile_usesDefaultProfile_andStarterBean() {
        try (var context = start()) {
            assertThat(context.getEnvironment().getActiveProfiles()).isEmpty();
            assertThat(context.getEnvironment().getDefaultProfiles()).containsExactly("default");
            assertThat(context.getBean(GreetingService.class)).isExactlyInstanceOf(GreetingService.class);
            assertThat(greet(context)).isEqualTo("Hello, Ada!");
        }
    }

    @Test
    void devProfile_profileSpecificFileOverridesApplicationYml() {
        try (var context = start("--spring.profiles.active=dev")) {
            assertThat(greet(context)).isEqualTo("Hey Ada, you are on dev");
            assertThat(context.getBean(GreetingService.class)).isExactlyInstanceOf(GreetingService.class);
        }
    }

    @Test
    void loudProfile_activatesProfileBean_andStarterBacksOff() {
        try (var context = start("--spring.profiles.active=loud")) {
            assertThat(context.getBeansOfType(GreetingService.class)).hasSize(1);
            assertThat(context.getBean(GreetingService.class)).isInstanceOf(LoudGreetingService.class);
            assertThat(greet(context)).isEqualTo("HELLO, ADA!");
        }
    }

    @Test
    void profileGroup_expandsToItsMembers() {
        try (var context = start("--spring.profiles.active=demo")) {
            assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("demo", "dev", "loud");
            assertThat(greet(context)).isEqualTo("HEY ADA, YOU ARE ON DEV");
        }
    }

    @Test
    void prodProfile_activatesSecondDocumentOfApplicationYml() {
        try (var context = start("--spring.profiles.active=prod")) {
            assertThat(greet(context)).isEqualTo("Good day, Ada.");
        }
    }

    private static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(DemoApplication.class).run(args);
    }

    private static String greet(ConfigurableApplicationContext context) {
        return context.getBean(GreetingService.class).greet("Ada");
    }
}
