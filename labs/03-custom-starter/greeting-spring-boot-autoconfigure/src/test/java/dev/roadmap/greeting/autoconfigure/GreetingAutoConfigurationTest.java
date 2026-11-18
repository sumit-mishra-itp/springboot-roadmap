package dev.roadmap.greeting.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.roadmap.greeting.GreetingService;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ApplicationContextRunner builds a tiny, fresh context per test: no @SpringBootApplication,
 * no component scan, just the auto-configuration plus whatever each test adds.
 */
class GreetingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GreetingAutoConfiguration.class));

    @Test
    void registersDefaultService_whenNothingIsConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GreetingService.class);
            assertThat(context.getBean(GreetingService.class).greet("Ada")).isEqualTo("Hello, Ada!"); // @DefaultValue
        });
    }

    @Test
    void bindsTemplate_fromProperty() {
        contextRunner.withPropertyValues("greeting.template=Hi %s")
                .run(context -> assertThat(context.getBean(GreetingService.class).greet("Ada")).isEqualTo("Hi Ada"));
    }

    @Test
    void backsOff_whenUserDefinesGreetingService() {
        contextRunner.withUserConfiguration(UserGreetingConfig.class).run(context -> {
            assertThat(context).hasSingleBean(GreetingService.class);      // not two: the auto-config backed off
            assertThat(context).getBean("customGreeting").isSameAs(context.getBean(GreetingService.class));
        });
    }

    @Test
    void backsOff_whenLibraryClassIsMissing() {
        contextRunner.withClassLoader(new FilteredClassLoader(GreetingService.class)).run(context -> {
            assertThat(context).doesNotHaveBean(GreetingService.class);
            assertThat(context).doesNotHaveBean(GreetingProperties.class); // whole class skipped, not just the @Bean
        });
    }

    @Test
    void backsOff_whenDisabledByProperty() {
        contextRunner.withPropertyValues("greeting.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(GreetingService.class));
    }

    @Test
    void failsFast_whenTemplateIsInvalid() {
        contextRunner.withPropertyValues("greeting.template=Hello").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().isInstanceOf(BindValidationException.class)
                    .hasMessageContaining("must contain %s");
        });
    }

    @Test
    void conditionEvaluationReport_recordsWhyAutoConfigurationDidNotMatch() {
        contextRunner.withPropertyValues("greeting.enabled=false").run(context -> {
            var report = ConditionEvaluationReport.get(context.getBeanFactory());
            var outcomes = report.getConditionAndOutcomesBySource().get(GreetingAutoConfiguration.class.getName());
            assertThat(outcomes.isFullMatch()).isFalse();
            assertThat(outcomes).anySatisfy(o -> assertThat(o.getOutcome().getMessage())
                    .contains("@ConditionalOnProperty (greeting.enabled=true) found different value"));
        });
    }

    @Test
    void conditionalOnBean_inUserConfiguration_cannotSeeAutoConfiguredBeans() {
        // User configuration is parsed BEFORE auto-configurations, so at that moment the
        // auto-configured GreetingService definition does not exist yet. This is why the docs say
        // to use @ConditionalOnBean/@ConditionalOnMissingBean only in auto-configuration classes.
        contextRunner.withUserConfiguration(DependsOnGreetingConfig.class).run(context -> {
            assertThat(context).hasSingleBean(GreetingService.class);
            assertThat(context).doesNotHaveBean("greetingBanner");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserGreetingConfig {
        @Bean
        GreetingService customGreeting() {
            return new GreetingService("Yo %s");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DependsOnGreetingConfig {
        @Bean
        @ConditionalOnBean(GreetingService.class)
        String greetingBanner() {
            return "banner";
        }
    }
}
