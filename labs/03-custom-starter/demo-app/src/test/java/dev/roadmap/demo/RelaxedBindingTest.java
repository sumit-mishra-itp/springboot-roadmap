package dev.roadmap.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import dev.roadmap.demo.config.AppProperties;
import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** Relaxed binding and nested validation, without starting the whole app. */
class RelaxedBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void camelCaseAndUnderscoreKeys_bindToTheSameKebabProperty() {
        runner.withPropertyValues("app.owner=team", "app.support.onCallEmail=camel@roadmap.dev", "app.support.response_time=30s")
                .run(context -> {
                    var support = context.getBean(AppProperties.class).support();
                    assertThat(support.onCallEmail()).isEqualTo("camel@roadmap.dev");
                    assertThat(support.responseTime()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void upperCaseEnvironmentVariable_bindsToCanonicalName() {
        var environment = fakeSystemEnvironment(Map.of("APP_SUPPORT_ONCALLEMAIL", "env@roadmap.dev"));

        assertThat(Binder.get(environment).bind("app.support.on-call-email", String.class).get())
                .isEqualTo("env@roadmap.dev");
        // what @Value("${app.support.on-call-email}") sees: the canonical name is mapped to the env var
        assertThat(environment.getProperty("app.support.on-call-email")).isEqualTo("env@roadmap.dev");
        // SystemEnvironmentPropertySource upper-cases any name itself, so even the camelCase form finds the
        // env var. Compare ConfigBindingTest: the same camelCase @Value key does NOT find a kebab key in YAML.
        assertThat(environment.getProperty("app.support.onCallEmail")).isEqualTo("env@roadmap.dev");
    }

    @Test
    void nestedRecord_isValidated_becauseOfValid() {
        runner.withPropertyValues("app.owner=team", "app.support.on-call-email=not-an-email").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().isInstanceOf(BindValidationException.class)
                    .hasMessageContaining("support.onCallEmail");
        });
    }

    /** Replaces the real OS environment with a fake one, under the name Boot treats as "the environment". */
    private static StandardEnvironment fakeSystemEnvironment(Map<String, Object> variables) {
        var environment = new StandardEnvironment();
        String name = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
        environment.getPropertySources().replace(name, new SystemEnvironmentPropertySource(name, variables));
        ConfigurationPropertySources.attach(environment);
        return environment;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class PropertiesConfig {
    }
}
