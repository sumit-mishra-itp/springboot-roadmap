package dev.roadmap.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import dev.roadmap.demo.config.AppProperties;
import dev.roadmap.demo.config.ValueSettings;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.util.unit.DataSize;

/** What application.yml turns into, through @ConfigurationProperties and through @Value. */
@SpringBootTest
class ConfigBindingTest {

    private final AppProperties app;
    private final ValueSettings values;
    private final Environment environment;

    ConfigBindingTest(@Autowired AppProperties app, @Autowired ValueSettings values, @Autowired Environment environment) {
        this.app = app;
        this.values = values;
        this.environment = environment;
    }

    @Test
    void configurationProperties_convertsDurationAndDataSize() {
        assertThat(app.support().responseTime()).isEqualTo(Duration.ofHours(4));         // "4h"
        assertThat(app.support().maxAttachmentSize()).isEqualTo(DataSize.ofMegabytes(10)); // "10MB"
    }

    @Test
    void placeholderDefault_isUsed_whenVariableIsMissing() {
        // app.owner: ${APP_OWNER:platform-team}  (assumes APP_OWNER is not set on the build machine)
        assertThat(app.owner()).isEqualTo("platform-team");
        assertThat(values.region()).isEqualTo("eu-west-1"); // default inside @Value("${app.region:eu-west-1}")
    }

    @Test
    void randomValue_isResolvedOncePerStart() {
        assertThat(app.instanceId()).matches("[0-9a-f-]{36}");
    }

    @Test
    void value_resolvesCanonicalKey_butNotCamelCaseAlias() {
        assertThat(values.canonicalKey()).isEqualTo("oncall@roadmap.dev");
        assertThat(values.camelCaseKey()).isEqualTo("not-found"); // @Value does not relax a camelCase name
    }

    @Test
    void spel_runsOnTheResolvedPlaceholder() {
        assertThat(values.ownerUpperCase()).isEqualTo("PLATFORM-TEAM");
    }

    @Test
    void placeholders_resolveNestedDefaults_andBackslashEscapes() {
        assertThat(environment.resolvePlaceholders("${app.missing:${app.owner}}")).isEqualTo("platform-team");
        assertThat(environment.resolvePlaceholders("\\${app.owner}")).isEqualTo("${app.owner}"); // Framework 6.2+ escape
    }
}
