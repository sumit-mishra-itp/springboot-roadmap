package dev.roadmap.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import dev.roadmap.demo.config.AppProperties;
import dev.roadmap.demo.greeting.LoudGreetingService;
import dev.roadmap.greeting.GreetingService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class DevProfileSpringBootTest {

    private final ApplicationContext context;

    DevProfileSpringBootTest(@Autowired ApplicationContext context) {
        this.context = context;
    }

    @Test
    void activeProfiles_loadsApplicationDevYml_andSkipsOtherProfileBeans() {
        assertThat(context.getBean(GreetingService.class).greet("Ada")).isEqualTo("Hey Ada, you are on dev");
        assertThat(context.getBean(AppProperties.class).support().responseTime()).isEqualTo(Duration.ofMinutes(15));
        assertThat(context.getBeansOfType(LoudGreetingService.class)).isEmpty(); // @Profile("loud") is not active
    }
}
