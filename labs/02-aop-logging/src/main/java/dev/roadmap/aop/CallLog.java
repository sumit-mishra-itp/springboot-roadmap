package dev.roadmap.aop;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/**
 * Shared, thread-safe recorder. Aspects and targets append events here so tests can assert the
 * exact order in which advice ran. It's a singleton shared by request threads, hence CopyOnWriteArrayList.
 */
@Component
public class CallLog {

    private final List<String> events = new CopyOnWriteArrayList<>();

    public void record(String event) {
        events.add(event);
    }

    public List<String> events() {
        return List.copyOf(events);
    }

    public void clear() {
        events.clear();
    }
}
