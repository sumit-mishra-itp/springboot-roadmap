package dev.roadmap.jpa.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import dev.roadmap.jpa.IntegrationTest;
import dev.roadmap.jpa.TestData;

@IntegrationTest
class LockingTest {

    private static final int STOCK = 5;
    private static final int BUYERS = 10;

    @Autowired InventoryService inventory;
    @Autowired ProductRepository products;
    @Autowired TransactionTemplate transaction;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        TestData.truncateAll(jdbc);
    }

    @Test
    void savingAStaleCopy_throwsObjectOptimisticLockingFailureException() {
        Long id = seedProduct();
        Product alice = products.findById(id).orElseThrow();   // detached copy, version 0
        Product bob = products.findById(id).orElseThrow();     // detached copy, version 0

        alice.changePrice(new BigDecimal("89.00"));
        products.save(alice);                                  // commits, version becomes 1

        bob.changePrice(new BigDecimal("79.00"));
        assertThatThrownBy(() -> products.save(bob))           // still claims version 0
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        Product current = products.findById(id).orElseThrow();
        assertThat(current.getPrice()).isEqualByComparingTo("89.00"); // Bob did not silently overwrite Alice
        assertThat(current.getVersion()).isEqualTo(1);
    }

    @Test
    void flashSale_optimisticLockingWithRetry_neverOversells() throws InterruptedException {
        flashSale(inventory::reserveOptimistic);
    }

    @Test
    void flashSale_pessimisticLocking_neverOversells() throws InterruptedException {
        flashSale(inventory::reservePessimistic);
    }

    @Test
    void pessimisticWriteLock_makesAConcurrentNoWaitLockFail() throws Exception {
        Long id = seedProduct();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);

        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<?> holder = pool.submit(() -> transaction.executeWithoutResult(status -> {
                products.findLockedById(id);                   // SELECT ... FOR UPDATE, held until commit
                locked.countDown();
                await(release);
            }));
            locked.await();
            try {
                assertThatThrownBy(() -> transaction.executeWithoutResult(status -> products.findLockedNoWaitById(id)))
                        .isInstanceOf(PessimisticLockingFailureException.class);
            } finally {
                release.countDown();
            }
            holder.get();
        }
    }

    /** 10 buyers race for 5 items, all released at the same instant. */
    private void flashSale(BiConsumer<Long, Integer> reserve) throws InterruptedException {
        Long id = seedProduct();
        var sold = new AtomicInteger();
        var rejected = new AtomicInteger();
        var start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(BUYERS)) {
            for (int i = 0; i < BUYERS; i++) {
                pool.submit(() -> {
                    await(start);
                    try {
                        reserve.accept(id, 1);
                        sold.incrementAndGet();
                    } catch (OutOfStockException e) {
                        rejected.incrementAndGet();
                    }
                });
            }
            start.countDown();
        } // close() waits for every buyer

        assertThat(sold).hasValue(STOCK);
        assertThat(rejected).hasValue(BUYERS - STOCK);
        assertThat(products.findById(id).orElseThrow().getStock()).isZero();
    }

    private Long seedProduct() {
        return transaction.execute(status -> TestData.product(em, "SKU-FLASH", "99.00", STOCK).getId());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
