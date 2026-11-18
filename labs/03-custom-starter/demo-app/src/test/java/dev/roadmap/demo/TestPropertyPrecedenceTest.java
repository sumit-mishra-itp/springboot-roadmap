package dev.roadmap.demo;

import static org.assertj.core.api.Assertions.assertThat;

import dev.roadmap.demo.config.AppProperties;
import dev.roadmap.greeting.GreetingService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Both test property mechanisms beat application.yml, and @TestPropertySource beats @SpringBootTest(properties). */
@SpringBootTest(properties = {"greeting.template=From @SpringBootTest %s", "app.owner=springboottest-properties"})
@TestPropertySource(properties = "app.owner=test-property-source")
class TestPropertyPrecedenceTest {

    private final GreetingService greetings;
    private final AppProperties app;

    TestPropertyPrecedenceTest(@Autowired GreetingService greetings, @Autowired AppProperties app) {
        this.greetings = greetings;
        this.app = app;
    }

    @Test
    void springBootTestProperties_beatApplicationYml() {
        assertThat(greetings.greet("Ada")).isEqualTo("From @SpringBootTest Ada");
    }

    @Test
    void testPropertySource_beatsSpringBootTestProperties() {
        assertThat(app.owner()).isEqualTo("test-property-source");
    }
}
