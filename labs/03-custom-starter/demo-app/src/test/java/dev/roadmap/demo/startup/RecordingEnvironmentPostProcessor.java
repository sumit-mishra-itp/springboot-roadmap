package dev.roadmap.demo.startup;

import java.util.HashMap;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.bootstrap.BootstrapContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Registered in META-INF/spring.factories under the Boot 4 key org.springframework.boot.EnvironmentPostProcessor
 * (it was org.springframework.boot.env.EnvironmentPostProcessor in Boot 3). It runs before the context exists,
 * so it cannot be a bean. Boot can pass it the BootstrapContext through the constructor.
 */
public class RecordingEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private final BootstrapContext bootstrapContext;

    public RecordingEnvironmentPostProcessor(BootstrapContext bootstrapContext) {
        this.bootstrapContext = bootstrapContext;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        StartupRecorder.record("EnvironmentPostProcessor");
        if (!bootstrapContext.isRegistered(FakeSecretStore.class)) {
            return;
        }
        var secrets = new HashMap<String, Object>(bootstrapContext.get(FakeSecretStore.class).secrets());
        environment.getPropertySources().addFirst(new MapPropertySource("fakeSecretStore", secrets));
    }
}
