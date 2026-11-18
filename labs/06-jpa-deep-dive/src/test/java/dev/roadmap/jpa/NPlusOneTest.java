package dev.roadmap.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import dev.roadmap.jpa.customer.Customer;
import dev.roadmap.jpa.customer.CustomerRepository;
import dev.roadmap.jpa.order.OrderRepository;
import dev.roadmap.jpa.order.PurchaseOrder;

/**
 * Counts real SQL statements with Hibernate Statistics (getPrepareStatementCount counts every JDBC
 * PreparedStatement, including lazy collection and proxy loads, unlike getQueryExecutionCount).
 */
@JpaSliceTest
class NPlusOneTest {

    private static final int CUSTOMERS = 5;
    private static final int ORDERS_EACH = 2;

    @Autowired TestEntityManager em;
    @Autowired CustomerRepository customers;
    @Autowired OrderRepository orders;

    private Statistics stats;

    @BeforeEach
    void seed() {
        TestData.customersWithOrders(em.getEntityManager(), CUSTOMERS, ORDERS_EACH);
        em.flush();
        em.clear();          // empty the first-level cache, or every "lazy load" would be served from memory
        stats = TestData.statistics(em.getEntityManager());
        stats.clear();
    }

    @Test
    void lazyCollection_inALoop_firesOneQueryPerParent() {
        List<Customer> all = customers.findAll();               // 1 query
        int orderCount = all.stream().mapToInt(c -> c.getOrders().size()).sum(); // +1 query PER customer

        assertThat(orderCount).isEqualTo(CUSTOMERS * ORDERS_EACH);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1 + CUSTOMERS);
    }

    @Test
    void lazyManyToOne_inALoop_firesOneQueryPerDistinctParent() {
        List<PurchaseOrder> all = orders.findAll();              // 1 query, 10 orders
        all.forEach(o -> o.getCustomer().getName());            // proxies: 1 query per DISTINCT customer

        assertThat(all).hasSize(CUSTOMERS * ORDERS_EACH);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1 + CUSTOMERS);
    }

    @Test
    void lazyProxy_getId_doesNotHitTheDatabase() {
        List<PurchaseOrder> all = orders.findAll();
        all.forEach(o -> o.getCustomer().getId());              // the id is already in the FK column

        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void joinFetch_loadsEverythingInOneQuery_withoutDuplicateRoots() {
        List<Customer> all = customers.findAllWithOrdersFetchJoin();
        int orderCount = all.stream().mapToInt(c -> c.getOrders().size()).sum();

        assertThat(all).hasSize(CUSTOMERS);                      // 10 joined rows, but Hibernate 6+ de-duplicates roots
        assertThat(orderCount).isEqualTo(CUSTOMERS * ORDERS_EACH);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void entityGraph_loadsEverythingInOneQuery() {
        List<Customer> all = customers.findAllWithOrdersEntityGraph();
        int orderCount = all.stream().mapToInt(c -> c.getOrders().size()).sum();

        assertThat(all).hasSize(CUSTOMERS);
        assertThat(orderCount).isEqualTo(CUSTOMERS * ORDERS_EACH);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void batchSize_loadsLazyCollectionsInChunks() {
        List<PurchaseOrder> all = orders.findAll();                         // 1 query, 10 orders
        int lineCount = all.stream().mapToInt(o -> o.getLines().size()).sum(); // @BatchSize(5): 10 / 5 = 2 queries

        assertThat(lineCount).isEqualTo(CUSTOMERS * ORDERS_EACH);
        assertThat(stats.getCollectionFetchCount()).isEqualTo(2);
        // + 1 surprise: OrderLine.product points at a @SoftDelete entity, so Hibernate loads it eagerly
        // (it must know whether the target is deleted). See MappingFeaturesTest.
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1 + 2 + 1);
    }
}
