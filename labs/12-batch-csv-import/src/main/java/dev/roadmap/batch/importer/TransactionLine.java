package dev.roadmap.batch.importer;

/** One raw CSV row, all strings. Parsing happens in the processor so a bad value is a countable skip, not a crash. */
public record TransactionLine(String id, String accountId, String amount, String currency, String bookedOn) {
}
