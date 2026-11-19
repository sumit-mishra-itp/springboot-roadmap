package dev.roadmap.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import dev.roadmap.shop.TestcontainersConfiguration;

/**
 * JPA slice on a real Postgres: Flyway runs V1, then Hibernate validates the entities against it, so this class
 * also proves the mapping matches the schema. Each test runs in a transaction that is rolled back.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ProductRepositoryTest {

    @Autowired
    ProductRepository products;

    @Autowired
    TestEntityManager em;

    Category peripherals;
    Category books;

    @BeforeEach
    void seed() {
        peripherals = em.persist(new Category("Peripherals"));
        books = em.persist(new Category("Books"));
        em.persist(product("KB-001", "29.90", peripherals));
        em.persist(product("MS-001", "19.90", peripherals));
        em.persist(product("BK-001", "45.00", books));
        em.flush();
        em.clear(); // forget everything, so the next read must hit the database
    }

    @Test
    void findAll_loadsTheCategoryWithTheProducts_soMappingAPageCausesNoExtraQueries() {
        Page<Product> page = products.findAll(PageRequest.of(0, 10, Sort.by("price")));

        assertThat(page.getContent()).extracting(Product::getSku).containsExactly("MS-001", "KB-001", "BK-001");
        // @EntityGraph fetched the LAZY association in the same SELECT: no proxy is left to initialize later
        assertThat(page.getContent()).allSatisfy(product -> assertThat(Hibernate.isInitialized(product.getCategory())).isTrue());
    }

    @Test
    void findByCategoryId_returnsOnlyThatCategory_withCorrectTotal() {
        Page<Product> page = products.findByCategoryId(peripherals.getId(), PageRequest.of(0, 1, Sort.by("sku")));

        assertThat(page.getContent()).extracting(Product::getSku).containsExactly("KB-001");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void duplicateSku_isRejectedByTheUniqueConstraint_atFlushNotAtSave() {
        // Sequence ids are pre-allocated, so save() needs no INSERT. The INSERT, and the violation, wait for the flush.
        products.save(product("KB-001", "1.00", em.find(Category.class, books.getId())));

        assertThatThrownBy(products::flush).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void negativeStock_isRejectedByTheCheckConstraint() {
        Product oversold = new Product("NEG-001", "Oversold", null, new BigDecimal("1.00"), -1, em.find(Category.class, books.getId()));

        assertThatThrownBy(() -> products.saveAndFlush(oversold)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void update_bumpsTheVersionColumn() {
        Product product = products.saveAndFlush(product("UPD-001", "5.00", em.find(Category.class, books.getId())));
        assertThat(product.getVersion()).isZero();

        product.replaceWith("UPD-001", "Renamed", null, new BigDecimal("6.00"), 3, product.getCategory());
        assertThat(product.getVersion()).as("version is bumped by the UPDATE at flush, not by the setter").isZero();
        products.flush();

        assertThat(product.getVersion()).isEqualTo(1);
    }

    private static Product product(String sku, String price, Category category) {
        return new Product(sku, "Product " + sku, null, new BigDecimal(price), 10, category);
    }
}
