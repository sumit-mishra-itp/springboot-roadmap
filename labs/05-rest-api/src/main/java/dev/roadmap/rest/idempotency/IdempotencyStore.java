package dev.roadmap.rest.idempotency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * Stripe-style idempotency keys. The client generates a key (a UUID) per logical operation and resends it on
 * every retry. The first request runs the action and stores the result. A retry with the same key and the same
 * body gets the stored result back, and a retry with a different body is rejected (422).
 */
@Component
public class IdempotencyStore {

    public record Outcome<T>(T value, boolean replayed) {
    }

    private record Entry(Object fingerprint, Object result) {
    }

    // ponytail: unbounded, no TTL, single node. In production use Redis SET NX EX or a DB table with a unique key,
    // expire after ~24h (Stripe's window), and scope keys per client/account.
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * computeIfAbsent is atomic per key, so two concurrent requests with one key run the action exactly once.
     * The second waits for the first to finish, then replays its result. If the action throws, nothing is stored
     * and the client may retry.
     */
    @SuppressWarnings("unchecked")
    public <T> Outcome<T> execute(String key, Object fingerprint, Supplier<T> action) {
        var ranNow = new AtomicBoolean();
        Entry entry = entries.computeIfAbsent(key, k -> {
            ranNow.set(true);
            return new Entry(fingerprint, action.get());
        });
        if (!entry.fingerprint().equals(fingerprint)) {
            throw new IdempotencyKeyReusedException(key);
        }
        return new Outcome<>((T) entry.result(), !ranNow.get());
    }
}
