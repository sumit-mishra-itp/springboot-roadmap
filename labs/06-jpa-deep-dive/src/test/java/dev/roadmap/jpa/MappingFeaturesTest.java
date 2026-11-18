package dev.roadmap.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.roadmap.jpa.catalog.Product;
import dev.roadmap.jpa.catalog.ProductRepository;
import dev.roadmap.jpa.customer.Customer;
import dev.roadmap.jpa.customer.CustomerRepository;
import dev.roadmap.jpa.order.OrderLine;
import dev.roadmap.jpa.order.PurchaseOrder;

/** JPA auditing, Hibernate @SoftDelete, and cascade + orphanRemoval. */
@JpaSliceTest
class MappingFeaturesTest {

    @Autowired TestEntityManager tem;
    @Autowired CustomerRepository customers;
    @Autowired ProductRepository products;
    @Autowired JdbcTemplate jdbc;

    private EntityManager em;

    @BeforeEach
    void setUp() {
        em = tem.getEntityManager();
    }

    @Test
    void auditing_setsCreatedAndLastModifiedDates() throws InterruptedException {
        Customer customer = customers.saveAndFlush(new Customer("Ada", "ada@example.com"));
        Instant created = customer.getCreatedAt();

        assertThat(created).isNotNull();
        assertThat(customer.getUpdatedAt()).isNotNull();            // modifyOnCreate defaults to true

        Thread.sleep(5);                                            // make the clock tick
        customer.rename("Ada Lovelace");
        customers.saveAndFlush(customer);                           // @PreUpdate fires because a field is dirty

        assertThat(customer.getCreatedAt()).isEqualTo(created);
        assertThat(customer.getUpdatedAt()).isAfter(created);
    }

    @Test
    void softDelete_hidesTheRowFromJpa_butKeepsItInTheTable() {
        Product product = TestData.product(em, "SKU-OLD", "5.00", 1);
        em.flush();

        products.delete(product);
        em.flush();
        em.clear();

        assertThat(products.findById(product.getId())).isEmpty();
        assertThat(products.count()).isZero();
        assertThat(products.countIncludingSoftDeleted()).isEqualTo(1);  // native SQL is not filtered
        assertThat(jdbc.queryForObject("select deleted from products where id = ?", Boolean.class, product.getId()))
                .isTrue();
    }

    @Test
    void lazyManyToOne_toASoftDeleteEntity_isLoadedEagerly() {
        Customer customer = TestData.customersWithOrders(em, 1, 1).getFirst();
        em.flush();
        Long orderId = customer.getOrders().getFirst().getId();
        Long lineId = customer.getOrders().getFirst().getLines().getFirst().getId();
        em.clear();
        var stats = TestData.statistics(em);
        stats.clear();

        PurchaseOrder order = em.find(PurchaseOrder.class, orderId);
        assertThat(Hibernate.isInitialized(order.getCustomer())).isFalse();  // LAZY works: a plain proxy
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);

        OrderLine line = em.find(OrderLine.class, lineId);
        assertThat(Hibernate.isInitialized(line.getProduct())).isTrue();     // LAZY ignored: Product is @SoftDelete
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1 + 2);       // line + product
    }

    @Test
    void orphanRemoval_deletesALineRemovedFromTheCollection() {
        Customer customer = TestData.customersWithOrders(em, 1, 1).getFirst();
        em.flush();
        PurchaseOrder order = customer.getOrders().getFirst();
        OrderLine line = order.getLines().getFirst();

        order.removeLine(line);                                     // no repository.delete()
        em.flush();

        assertThat(em.find(OrderLine.class, line.getId())).isNull();
    }
}
