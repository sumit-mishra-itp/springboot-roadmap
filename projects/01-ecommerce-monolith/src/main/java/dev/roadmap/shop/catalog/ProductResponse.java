package dev.roadmap.shop.catalog;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/** version is exposed so clients can see that a write happened (it is the optimistic-lock counter). */
public record ProductResponse(long id, String sku, String name, @Nullable String description, BigDecimal price, int stock,
                              long version, long categoryId, String categoryName) {

    /** Touches product.getCategory(), so call it inside the transaction or on a product loaded with its category. */
    static ProductResponse from(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getDescription(),
                product.getPrice(), product.getStock(), product.getVersion(), category.getId(), category.getName());
    }
}
