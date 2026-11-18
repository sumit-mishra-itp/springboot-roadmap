package dev.roadmap.aop.inventory;

/** A transient failure (timeout, 503) that is worth retrying. */
public class DownstreamUnavailableException extends RuntimeException {

    public DownstreamUnavailableException(String message) {
        super(message);
    }
}
