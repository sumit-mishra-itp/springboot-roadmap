package dev.roadmap.batch.importer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.dao.TransientDataAccessResourceException;

/**
 * Validates and converts one CSV row. The three outcomes are counted separately by Spring Batch:
 * <ul>
 *   <li>return a {@link Transaction}: written;</li>
 *   <li>return {@code null}: <b>filtered</b> (filterCount). Used for zero-amount rows, which are valid but pointless;</li>
 *   <li>throw {@link InvalidTransactionException}: <b>skipped</b> (processSkipCount), because the step declares it skippable.</li>
 * </ul>
 * The two "chaos" settings are lab-only fault injection, passed as non-identifying job parameters, so the tests can
 * prove retry and restart against real infrastructure. A production processor would not have them.
 */
public class TransactionProcessor implements ItemProcessor<TransactionLine, Transaction> {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("EUR", "GBP", "USD", "INR");

    private final String transientFailId;
    private final String crashOnId;
    private final AtomicBoolean transientFailureDone = new AtomicBoolean();

    public TransactionProcessor(String transientFailId, String crashOnId) {
        this.transientFailId = transientFailId;
        this.crashOnId = crashOnId;
    }

    @Override
    public Transaction process(TransactionLine line) {
        injectFaults(line.id());
        if (!SUPPORTED_CURRENCIES.contains(line.currency())) {
            throw new InvalidTransactionException("unsupported currency " + line.currency() + " in " + line.id());
        }
        BigDecimal amount = parseAmount(line);
        if (amount.signum() == 0) {
            return null;
        }
        return new Transaction(line.id(), line.accountId(), amount, line.currency(), parseDate(line));
    }

    private void injectFaults(String id) {
        if (id.equals(transientFailId) && transientFailureDone.compareAndSet(false, true)) {
            // Looks like a dropped connection or a deadlock victim: worth retrying.
            throw new TransientDataAccessResourceException("simulated transient failure on " + id);
        }
        if (id.equals(crashOnId)) {
            // Neither skippable nor retryable, so the step fails and the job becomes restartable.
            throw new IllegalStateException("simulated crash on " + id);
        }
    }

    private static BigDecimal parseAmount(TransactionLine line) {
        try {
            return new BigDecimal(line.amount());
        } catch (NumberFormatException e) {
            throw new InvalidTransactionException("bad amount '" + line.amount() + "' in " + line.id());
        }
    }

    private static LocalDate parseDate(TransactionLine line) {
        try {
            return LocalDate.parse(line.bookedOn());
        } catch (DateTimeParseException e) {
            throw new InvalidTransactionException("bad date '" + line.bookedOn() + "' in " + line.id());
        }
    }
}
