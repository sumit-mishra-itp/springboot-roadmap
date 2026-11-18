package dev.roadmap.rest.idempotency;

/** The same Idempotency-Key was sent with a different request body. That is a client bug, mapped to 422. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request body");
    }
}
