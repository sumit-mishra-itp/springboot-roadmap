package dev.roadmap.greeting;

/**
 * The "library" the starter configures. In a real starter this class would live in its own
 * jar (think {@code HikariDataSource} or {@code KafkaTemplate}); the auto-configuration only
 * activates when it is on the classpath.
 */
public class GreetingService {

    private final String template;

    public GreetingService(String template) {
        this.template = template;
    }

    public String greet(String name) {
        return template.formatted(name);
    }

    public String template() {
        return template;
    }
}
