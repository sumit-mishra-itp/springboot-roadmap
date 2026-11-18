package dev.roadmap.demo.startup;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Collects startup callbacks in the order they fire. Static because some run before any bean exists. */
final class StartupRecorder {

    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    private StartupRecorder() {
    }

    static void record(String event) {
        EVENTS.add(event);
    }

    static List<String> events() {
        return List.copyOf(EVENTS);
    }

    static void clear() {
        EVENTS.clear();
    }
}
