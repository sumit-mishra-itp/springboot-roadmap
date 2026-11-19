package dev.roadmap.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Plain JUnit: the in-memory stock rule. The race between two transactions is OversellConcurrencyTest's job. */
class ProductStockTest {

    private final Product product = new Product("KB-001", "Keyboard", null, new BigDecimal("10.00"), 2, new Category("Peripherals"));

    @Test
    void decrementStock_whenEnoughStock_reducesIt() {
        product.decrementStock(2);

        assertThat(product.getStock()).isZero();
    }

    @Test
    void decrementStock_beyondStock_throws_andLeavesStockUnchanged() {
        assertThatThrownBy(() -> product.decrementStock(3))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("has 2 in stock, 3 requested");

        assertThat(product.getStock()).isEqualTo(2);
    }
}
