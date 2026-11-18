package dev.roadmap.rest.book;

/** If-Match carried an old version: someone else updated the book first (lost-update prevention). */
public class StaleVersionException extends RuntimeException {

    public StaleVersionException(long id, long expected, long actual) {
        super("Book " + id + " is at version " + actual + ", but If-Match expected " + expected);
    }
}
