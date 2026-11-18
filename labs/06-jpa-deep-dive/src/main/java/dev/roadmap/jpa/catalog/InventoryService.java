package dev.roadmap.jpa.catalog;

import java.time.Duration;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Two ways to sell the last items of a flash sale without overselling. */
@Service
public class InventoryService {

    private final ProductRepository products;
    private final TransactionTemplate transaction;
    private final RetryTemplate retry;

    public InventoryService(ProductRepository products, TransactionTemplate transaction) {
        this.products = products;
        this.transaction = transaction;
        // Retry ONLY version conflicts. OutOfStockException is a business answer, not a transient failure.
        this.retry = new RetryTemplate(RetryPolicy.builder()
                .includes(ObjectOptimisticLockingFailureException.class)
                .maxRetries(20)
                .delay(Duration.ofMillis(5))
                .jitter(Duration.ofMillis(5))
                .build());
    }

    /**
     * Optimistic: no lock is held while reading. A conflicting commit makes the UPDATE ... WHERE version = ?
     * hit 0 rows, so the whole transaction is retried. The retry must wrap the transaction, not run inside it:
     * after a failure the persistence context is unusable and has to be thrown away.
     */
    public void reserveOptimistic(Long productId, int quantity) {
        retry.invoke(() -> transaction.executeWithoutResult(status ->
                products.findById(productId).orElseThrow().decrementStock(quantity)));
    }

    /** Pessimistic: SELECT ... FOR UPDATE serialises buyers on the row. No retries, but buyers queue. */
    @Transactional
    public void reservePessimistic(Long productId, int quantity) {
        products.findLockedById(productId).orElseThrow().decrementStock(quantity);
    }
}
