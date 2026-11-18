package dev.roadmap.greeting.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;

/** Proves the build artifacts a starter relies on: the imports file and the two generated metadata files. */
class StarterPackagingTest {

    private final ClassLoader classLoader = getClass().getClassLoader();

    @Test
    void importsFile_isDiscoveredByImportCandidates() {
        // The same call AutoConfigurationImportSelector makes at startup.
        var candidates = ImportCandidates.load(AutoConfiguration.class, classLoader).getCandidates();
        assertThat(candidates).contains(GreetingAutoConfiguration.class.getName());
    }

    @Test
    void configurationProcessor_generatesMetadataWithRecordParamDescriptions() throws IOException {
        String metadata = readAll("META-INF/spring-configuration-metadata.json");
        assertThat(metadata)
                .contains("\"name\": \"greeting.template\"")
                .contains("Message template; must contain a single %s placeholder for the name") // from @param
                .contains("\"defaultValue\": \"Hello, %s!\"");                                      // from @DefaultValue
    }

    @Test
    void autoconfigureProcessor_generatesConditionMetadata() throws IOException {
        // Lets Boot discard the auto-config from the class name alone, before loading the class.
        assertThat(readAll("META-INF/spring-autoconfigure-metadata.properties"))
                .contains(GreetingAutoConfiguration.class.getName() + ".ConditionalOnClass=dev.roadmap.greeting.GreetingService");
    }

    /** Several jars ship a file with this name (spring-boot does too), so concatenate all of them. */
    private String readAll(String resource) throws IOException {
        var all = new StringBuilder();
        for (URL url : Collections.list(classLoader.getResources(resource))) {
            try (InputStream in = url.openStream()) {
                all.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return all.toString();
    }
}
