package dev.roadmap.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import dev.roadmap.jpa.customer.Customer;
import dev.roadmap.jpa.customer.CustomerRepository;

/**
 * The global switch: default_batch_fetch_size applies @BatchSize to EVERY lazy association and proxy.
 * A different property set means a separate Spring context, hence its own container.
 */
@JpaSliceTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=16")
class DefaultBatchFetchSizeTest {

    @Autowired TestEntityManager em;
    @Autowired CustomerRepository customers;

    @Test
    void defaultBatchFetchSize_turnsNPlusOneIntoOnePlusOne() {
        TestData.customersWithOrders(em.getEntityManager(), 5, 2);
        em.flush();
        em.clear();
        var stats = TestData.statistics(em.getEntityManager());
        stats.clear();

        List<Customer> all = customers.findAll();
        int orderCount = all.stream().mapToInt(c -> c.getOrders().size()).sum();

        assertThat(orderCount).isEqualTo(10);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2); // same code as the N+1 test: 6 statements there
    }
}
