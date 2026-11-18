package dev.roadmap.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;

import dev.roadmap.jpa.catalog.Product;
import dev.roadmap.jpa.catalog.ProductRepository;
import dev.roadmap.jpa.customer.Customer;
import dev.roadmap.jpa.customer.CustomerRepository;
import dev.roadmap.jpa.customer.CustomerSummary;
import dev.roadmap.jpa.customer.CustomerView;
import dev.roadmap.jpa.order.OrderRepository;
import dev.roadmap.jpa.order.OrderTotal;

/** Projections, paging vs slicing vs keyset scrolling, and @Modifying bulk updates. */
@JpaSliceTest
class QueryFeaturesTest {

    @Autowired TestEntityManager tem;
    @Autowired CustomerRepository customers;
    @Autowired OrderRepository orders;
    @Autowired ProductRepository products;

    private EntityManager em;
    private Statistics stats;

    @BeforeEach
    void setUp() {
        em = tem.getEntityManager();
        stats = TestData.statistics(em);
    }

    // --- Projections -----------------------------------------------------------------------------

    @Test
    void interfaceProjection_returnsAProxy_notTheEntity() {
        seedCustomers(3);

        List<CustomerSummary> summaries = customers.findSummariesByNameStartingWithOrderByNameAsc("Page");

        assertThat(summaries).extracting(CustomerSummary::getName).containsExactly("Page 0", "Page 1", "Page 2");
        assertThat(summaries.getFirst()).isNotInstanceOf(Customer.class);
    }

    @Test
    void recordProjection_returnsImmutableDtos() {
        seedCustomers(2);

        List<CustomerView> views = customers.findViewsByEmailEndingWithOrderByNameAsc("@paging.example");

        assertThat(views).containsExactly(
                new CustomerView("Page 0", "page0@paging.example"),
                new CustomerView("Page 1", "page1@paging.example"));
    }

    @Test
    void constructorExpression_aggregatesStraightIntoARecord() {
        TestData.customersWithOrders(em, 1, 2);                    // order 1: 1 x 10.00, order 2: 2 x 10.00
        em.flush();

        List<OrderTotal> totals = orders.findTotals();

        assertThat(totals).extracting(OrderTotal::total)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("20.00"));
    }

    // --- Paging ----------------------------------------------------------------------------------

    @Test
    void page_runsAContentQueryAndACountQuery() {
        seedCustomers(5);
        stats.clear();

        Page<Customer> page = customers.findPageByNameStartingWith("Page", PageRequest.of(0, 2, Sort.by("name")));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2);  // SELECT ... LIMIT, then SELECT count(*)
    }

    @Test
    void page_skipsTheCountQuery_whenTheTotalIsObviousFromTheContent() {
        seedCustomers(5);
        stats.clear();

        Page<Customer> lastPage = customers.findPageByNameStartingWith("Page", PageRequest.of(2, 2, Sort.by("name")));

        assertThat(lastPage.getContent()).hasSize(1);               // a partial last page
        assertThat(lastPage.getTotalElements()).isEqualTo(5);       // offset 4 + 1 row
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);  // no COUNT needed
    }

    @Test
    void slice_neverRunsACountQuery() {
        seedCustomers(5);
        stats.clear();

        Slice<Customer> slice = customers.findSliceByNameStartingWith("Page", PageRequest.of(0, 2, Sort.by("name")));

        assertThat(slice.getContent()).hasSize(2);
        assertThat(slice.hasNext()).isTrue();                       // learned by fetching 3 rows
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void keysetScrolling_walksWindowsWithoutOffset() {
        seedCustomers(5);

        Window<Customer> window = customers.findFirst2ByOrderByIdAsc(ScrollPosition.keyset());
        int windows = 1;
        int seen = window.size();
        while (window.hasNext()) {
            window = customers.findFirst2ByOrderByIdAsc(window.positionAt(window.size() - 1));
            windows++;
            seen += window.size();
        }

        assertThat(seen).isEqualTo(5);
        assertThat(windows).isEqualTo(3);                           // 2 + 2 + 1
    }

    @Test
    void keysetScrolling_isStableWhenEarlierRowsAreDeleted() {
        List<Customer> seeded = seedCustomers(5);
        Window<Customer> first = customers.findFirst2ByOrderByIdAsc(ScrollPosition.keyset());

        em.remove(seeded.getFirst());                               // delete a row BEFORE the cursor
        em.flush();
        Window<Customer> second = customers.findFirst2ByOrderByIdAsc(first.positionAt(first.size() - 1));

        // OFFSET 2 would now skip "Page 2". The keyset (id > last seen id) does not.
        assertThat(second.getContent()).extracting(Customer::getName).containsExactly("Page 2", "Page 3");
    }

    // --- Bulk updates ----------------------------------------------------------------------------

    @Test
    void modifyingQuery_bypassesThePersistenceContext() {
        Product product = TestData.product(em, "SKU-BULK", "10.00", 5);
        em.flush();

        int updated = products.applyPriceFactor(new BigDecimal("2"));

        assertThat(updated).isEqualTo(1);
        assertThat(product.getPrice()).isEqualByComparingTo("10.00");  // managed instance is now STALE
        assertThat(products.findById(product.getId()).orElseThrow().getPrice())
                .isEqualByComparingTo("10.00");                        // findById is served from the stale cache
    }

    @Test
    void modifyingQuery_withClearAutomatically_readsFreshState_butSkipsVersioning() {
        Product product = TestData.product(em, "SKU-BULK", "10.00", 5);
        em.flush();

        products.applyPriceFactorAndClear(new BigDecimal("2"));

        Product reloaded = products.findById(product.getId()).orElseThrow();
        assertThat(reloaded).isNotSameAs(product);                     // the context was cleared
        assertThat(reloaded.getPrice()).isEqualByComparingTo("20.00");
        assertThat(reloaded.getVersion()).isZero();                    // bulk JPQL does not bump @Version
    }

    private List<Customer> seedCustomers(int count) {
        var seeded = new ArrayList<Customer>();
        for (int i = 0; i < count; i++) {
            var customer = new Customer("Page " + i, "page" + i + "@paging.example");
            em.persist(customer);
            seeded.add(customer);
        }
        em.flush();
        return seeded;
    }
}
