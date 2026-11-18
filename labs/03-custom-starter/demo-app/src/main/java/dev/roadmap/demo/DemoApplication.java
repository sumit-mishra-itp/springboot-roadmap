package dev.roadmap.demo;

import dev.roadmap.demo.config.AppProperties;
import dev.roadmap.greeting.GreetingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan // registers every @ConfigurationProperties class under this package
public class DemoApplication {

    private static final Logger log = LoggerFactory.getLogger(DemoApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    ApplicationRunner greetOwner(GreetingService greetings, AppProperties app) {
        return args -> log.info("{} (service: {})", greetings.greet(app.owner()), greetings.getClass().getSimpleName());
    }
}
