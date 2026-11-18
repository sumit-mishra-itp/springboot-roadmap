package dev.roadmap.demo.startup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import dev.roadmap.demo.DemoApplication;
import org.junit.jupiter.api.Test;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.bootstrap.BootstrapRegistry.InstanceSupplier;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.SpringApplicationEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

/** Proves the order of the SpringApplication.run() extension points that note 03 describes. */
class StartupOrderTest {

    @Test
    void springApplicationRun_firesExtensionPointsInThisOrder() {
        StartupRecorder.clear();
        ApplicationListener<ApplicationEvent> listener = event -> {
            if (event instanceof AvailabilityChangeEvent<?> availability) {
                StartupRecorder.record("AvailabilityChangeEvent " + availability.getState());
            }
            else if (event instanceof SpringApplicationEvent || event instanceof ContextRefreshedEvent) {
                StartupRecorder.record(event.getClass().getSimpleName());
            }
        };

        var app = new SpringApplicationBuilder(DemoApplication.class)
                .addBootstrapRegistryInitializer(registry -> {
                    StartupRecorder.record("BootstrapRegistryInitializer");
                    registry.register(FakeSecretStore.class,
                            InstanceSupplier.of(new FakeSecretStore(Map.of("demo.secret", "s3cr3t"))));
                })
                .initializers(context -> {
                    StartupRecorder.record("ApplicationContextInitializer");
                    context.getBeanFactory().registerSingleton("recordingRunner",
                            (ApplicationRunner) args -> StartupRecorder.record("ApplicationRunner"));
                })
                .listeners(listener);

        try (var context = app.run()) {
            // the EnvironmentPostProcessor pulled a value out of the bootstrap registry into the Environment
            assertThat(context.getEnvironment().getProperty("demo.secret")).isEqualTo("s3cr3t");
        }

        assertThat(StartupRecorder.events()).containsExactly(
                "BootstrapRegistryInitializer",
                "ApplicationStartingEvent",
                "EnvironmentPostProcessor",
                "ApplicationEnvironmentPreparedEvent",
                "ApplicationContextInitializer",
                "ApplicationContextInitializedEvent",
                "ApplicationPreparedEvent",
                "ContextRefreshedEvent",
                "ApplicationStartedEvent",
                "AvailabilityChangeEvent CORRECT",
                "ApplicationRunner",
                "ApplicationReadyEvent",
                "AvailabilityChangeEvent ACCEPTING_TRAFFIC");
    }
}
