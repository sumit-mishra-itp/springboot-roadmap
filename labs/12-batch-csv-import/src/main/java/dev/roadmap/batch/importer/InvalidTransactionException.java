package dev.roadmap.batch.importer;

/** A row that can never be imported. Declared skippable in the step, so it is counted and logged, not fatal. */
public class InvalidTransactionException extends RuntimeException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
