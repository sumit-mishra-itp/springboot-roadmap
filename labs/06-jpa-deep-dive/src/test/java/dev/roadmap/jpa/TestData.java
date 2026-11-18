package dev.roadmap.jpa;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import org.springframework.jdbc.core.JdbcTemplate;

import dev.roadmap.jpa.catalog.Category;
import dev.roadmap.jpa.catalog.Product;
import dev.roadmap.jpa.customer.Customer;
import dev.roadmap.jpa.order.PurchaseOrder;

/** Seed helpers shared by slice tests (TestEntityManager) and integration tests (EntityManager in a TransactionTemplate). */
public final class TestData {

    private TestData() {
    }

    public static Product product(EntityManager em, String sku, String price, int stock) {
        var category = new Category("category-" + sku);
        em.persist(category);
        var product = new Product(sku, "Product " + sku, new BigDecimal(price), stock, category);
        em.persist(product);
        return product;
    }

    /** {@code customerCount} customers, each with {@code ordersEach} orders of one line. Returns the customers. */
    public static List<Customer> customersWithOrders(EntityManager em, int customerCount, int ordersEach) {
        var product = product(em, "SKU-SEED", "10.00", 1_000);
        var customers = new ArrayList<Customer>();
        for (int c = 0; c < customerCount; c++) {
            var customer = new Customer("Customer " + c, "customer" + c + "@example.com");
            em.persist(customer);
            for (int o = 0; o < ordersEach; o++) {
                var order = new PurchaseOrder(customer);
                order.addLine(product, o + 1);
                em.persist(order); // cascades to lines
            }
            customers.add(customer);
        }
        return customers;
    }

    /** Hibernate's own counters. generate_statistics=true is set in application.properties. */
    public static Statistics statistics(EntityManager em) {
        return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    public static void truncateAll(JdbcTemplate jdbc) {
        jdbc.execute("truncate table order_lines, orders, customers, products, categories, audit_log, tags restart identity cascade");
    }
}
