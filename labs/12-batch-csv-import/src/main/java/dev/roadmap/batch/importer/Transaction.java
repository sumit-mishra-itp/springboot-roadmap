package dev.roadmap.batch.importer;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A validated transaction, ready for the JdbcBatchItemWriter (record components map to :named parameters). */
public record Transaction(String id, String accountId, BigDecimal amount, String currency, LocalDate bookedOn) {
}
