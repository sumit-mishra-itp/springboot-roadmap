package dev.roadmap.shop;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import dev.roadmap.shop.catalog.ProductResponse;
import dev.roadmap.shop.common.ApiExceptionHandler;
import dev.roadmap.shop.order.PlaceOrderRequest;
import dev.roadmap.shop.order.PlaceOrderRequest.Line;

/**
 * The oversell race, for real: separate HTTP requests, so separate threads and separate transactions on a real
 * Postgres, with nothing mocked and no test hook in production code.
 *
 * A start latch alone does NOT prove @Version works. The second buyer might simply read AFTER the first one committed,
 * see stock 0, and get "insufficient-stock", which is a 409 too. To force the dangerous interleaving, the test holds a
 * row lock on the product (SELECT ... FOR UPDATE on its own connection) while both buyers run:
 *   1. Both requests read the product (plain SELECTs are not blocked by a row lock in Postgres): stock 1, version 0.
 *   2. Both decrement in memory and reach commit. Hibernate flushes INSERTs first, and the order_lines INSERT's
 *      foreign-key check needs a lock on the product row, so both block behind the test's lock.
 *   3. When pg_stat_activity shows both blocked, the test rolls its lock back and lets them go.
 *   4. One "UPDATE products SET stock = 0, version = 1 WHERE id = ? AND version = 0" commits. The other UPDATE
 *      re-checks its WHERE against the committed row, matches 0 rows, and Hibernate turns that into
 *      ObjectOptimisticLockingFailureException, which ApiExceptionHandler maps to 409 concurrent-update.
 */
class OversellConcurrencyTest extends ApiTestSupport {

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcClient jdbc;

    @Test
    void twoBuyersRaceForTheLastUnit_exactlyOneWins_andTheOtherGets409ConcurrentUpdate() throws Exception {
        String admin = adminToken();
        ProductResponse lastUnit = createProduct(admin, createCategory(admin), "49.00", 1);
        Customer alice = newCustomer();
        Customer bob = newCustomer();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EntityExchangeResult<String>>> buyers = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(2); Connection lock = dataSource.getConnection()) {
            lock.setAutoCommit(false);
            lockRow(lock, lastUnit.id());
            try {
                buyers.add(pool.submit(afterLatch(start, () -> buyOne(alice, lastUnit.id()))));
                buyers.add(pool.submit(afterLatch(start, () -> buyOne(bob, lastUnit.id()))));
                start.countDown();
                awaitTransactionsWaitingOnALock(2);
            } finally {
                lock.rollback(); // always release, or a failed assertion above would hang both requests
            }

            List<EntityExchangeResult<String>> results = new ArrayList<>();
            for (Future<EntityExchangeResult<String>> buyer : buyers) {
                results.add(buyer.get(30, SECONDS));
            }

            assertThat(results).extracting(result -> result.getStatus().value()).containsExactlyInAnyOrder(201, 409);
            EntityExchangeResult<String> loser = results.stream().filter(r -> r.getStatus().value() == 409).findFirst().orElseThrow();
            assertThat(loser.getResponseHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(loser.getResponseBody())
                    .as("the loser read stock 1 too, so it must lose on @Version, not on the stock check")
                    .contains("\"type\":\"" + ApiExceptionHandler.PROBLEM_BASE + "concurrent-update\"");
        }

        assertThat(stock(lastUnit.id())).isZero();
        assertThat(unitsSold(lastUnit.id())).as("the loser's order rolled back with its stock change").isEqualTo(1);
    }

    /** No forced interleaving here: a free-for-all where the invariant must hold whatever the timing. */
    @Test
    void manyBuyersRaceForThreeUnits_stockNeverGoesNegative_andEveryUnitSoldIsAccountedFor() throws Exception {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, createCategory(admin), "5.00", 3);
        Customer customer = newCustomer();
        int buyerCount = 8; // below Hikari's default pool of 10, so every buyer gets a connection at once
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EntityExchangeResult<String>>> buyers = new ArrayList<>();
        List<Integer> statuses = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(buyerCount)) {
            for (int i = 0; i < buyerCount; i++) {
                buyers.add(pool.submit(afterLatch(start, () -> buyOne(customer, product.id()))));
            }
            start.countDown();
            for (Future<EntityExchangeResult<String>> buyer : buyers) {
                statuses.add(buyer.get(30, SECONDS).getStatus().value());
            }
        }

        long sold = statuses.stream().filter(status -> status == 201).count();
        assertThat(statuses).as("losers get 409 (concurrent-update or insufficient-stock), never a 500").containsOnly(201, 409);
        assertThat(sold).isBetween(1L, 3L);
        assertThat(stock(product.id())).isEqualTo(3 - sold).isGreaterThanOrEqualTo(0);
        assertThat(unitsSold(product.id())).isEqualTo(sold);
    }

    private EntityExchangeResult<String> buyOne(Customer customer, long productId) {
        return client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .headers(bearer(customer.accessToken()))
                .body(new PlaceOrderRequest(List.of(new Line(productId, 1))))
                .exchange()
                .expectBody(String.class).returnResult();
    }

    private static <T> Callable<T> afterLatch(CountDownLatch start, Callable<T> task) {
        return () -> {
            start.await();
            return task.call();
        };
    }

    private static void lockRow(Connection connection, long productId) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("select stock from products where id = ? for update")) {
            select.setLong(1, productId);
            select.executeQuery().close();
        }
    }

    /** Polls Postgres until the given number of backends are blocked on a lock (here: behind our row lock). */
    private void awaitTransactionsWaitingOnALock(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            int waiting = jdbc.sql("select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'")
                    .query(Integer.class).single();
            if (waiting >= expected) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Expected " + expected + " transactions blocked on the product row lock");
    }

    private int stock(long productId) {
        return jdbc.sql("select stock from products where id = ?").param(productId).query(Integer.class).single();
    }

    private long unitsSold(long productId) {
        return jdbc.sql("select coalesce(sum(quantity), 0) from order_lines where product_id = ?")
                .param(productId).query(Long.class).single();
    }
}
