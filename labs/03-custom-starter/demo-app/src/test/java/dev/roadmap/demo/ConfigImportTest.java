package dev.roadmap.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.roadmap.demo.config.AppProperties;
import dev.roadmap.demo.config.ValueSettings;
import dev.roadmap.greeting.GreetingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** spring.config.import with configtree: (Kubernetes-style mounted files) and with a .env file. */
class ConfigImportTest {

    @Test
    void configTree_turnsEachFileIntoAProperty(@TempDir Path mount) throws IOException {
        // Kubernetes mounts a ConfigMap or Secret as one file per key, e.g. /etc/config/greeting/template
        Files.createDirectories(mount.resolve("greeting"));
        Files.writeString(mount.resolve("greeting/template"), "From a mounted file, %s\n"); // trailing newline is trimmed

        try (var context = start("--spring.config.import=optional:configtree:" + slashed(mount) + "/")) {
            assertThat(context.getBean(GreetingService.class).greet("Ada")).isEqualTo("From a mounted file, Ada");
        }
    }

    @Test
    void dotEnvFile_needsTheExtensionHint_andFeedsPlaceholders(@TempDir Path dir) throws IOException {
        Path dotEnv = dir.resolve(".env");
        Files.writeString(dotEnv, "APP_OWNER=dotenv-team\nAPP_REGION=us-east-1\n");

        try (var context = start("--spring.config.import=optional:file:" + slashed(dotEnv) + "[.properties]")) {
            // application.yml says app.owner: ${APP_OWNER:platform-team}, and the .env key matches it literally
            assertThat(context.getBean(AppProperties.class).owner()).isEqualTo("dotenv-team");
            // but a .env file is NOT the OS environment: APP_REGION is not relaxed into app.region
            assertThat(context.getBean(ValueSettings.class).region()).isEqualTo("eu-west-1");
        }
    }

    @Test
    void missingOptionalImport_isIgnored() {
        try (var context = start("--spring.config.import=optional:file:./does-not-exist.yml")) {
            assertThat(context.getBean(GreetingService.class).greet("Ada")).isEqualTo("Hello, Ada!");
        }
    }

    private static ConfigurableApplicationContext start(String... args) {
        return new SpringApplicationBuilder(DemoApplication.class).run(args);
    }

    private static String slashed(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }
}
