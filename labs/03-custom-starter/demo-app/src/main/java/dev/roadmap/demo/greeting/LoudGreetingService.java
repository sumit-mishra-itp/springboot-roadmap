package dev.roadmap.demo.greeting;

import java.util.Locale;

import dev.roadmap.greeting.GreetingService;

/** The app's own GreetingService. Because it is assignable to GreetingService, the starter's bean backs off. */
public class LoudGreetingService extends GreetingService {

    public LoudGreetingService(String template) {
        super(template);
    }

    @Override
    public String greet(String name) {
        return super.greet(name).toUpperCase(Locale.ROOT);
    }
}
